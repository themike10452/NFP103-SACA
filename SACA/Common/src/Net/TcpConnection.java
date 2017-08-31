package Net;

import Utils.StringUtils;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Observable;
import java.util.Set;

/**
 * Created by Mike on 7/10/2017.
 */
public class TcpConnection extends Observable {

    private static final int SOCKET_TIMEOUT = 10000;

    private Socket m_Socket;
    private final ReaderThread m_ReadThread;
    private final WriterThread m_WriteThread;
    private final PingThread m_PingThread;
    private final ArrayList<String> m_MessageQueue;
    private final Set<EventHandler> m_EventHandlers;

    private final Object m_Mutex = new Object();

    public TcpConnection(Socket socket, int timeout) throws IOException {
        m_Socket = socket;
        m_Socket.setSoTimeout(timeout);

        m_MessageQueue = new ArrayList<>(10);
        m_EventHandlers = new HashSet<>(5);

        m_ReadThread = new ReaderThread(m_Socket.getInputStream());
        m_WriteThread = new WriterThread(m_Socket.getOutputStream());
        m_PingThread = new PingThread();

        m_ReadThread.start();
        m_WriteThread.start();
        m_PingThread.start();
    }

    public TcpConnection(Socket socket) throws IOException {
        this(socket, SOCKET_TIMEOUT);
    }

    public TcpConnection(String host, int port, int timeout) throws IOException {
        this(new Socket(host, port), timeout);
    }

    public TcpConnection(String host, int port) throws IOException {
        this(new Socket(host, port));
    }

    public void addEventHandler(EventHandler handler) {
        m_EventHandlers.add(handler);
    }

    public void removeEventHandler(EventHandler handler) {
        m_EventHandlers.remove(handler);
    }

    public void send(String message) {
        synchronized (m_Mutex) {
            m_MessageQueue.add(message);
            m_Mutex.notify();
        }
    }

    public void close() {
        try {
            if (m_Socket == null) return;

            synchronized (m_Mutex) {
                m_Socket.close();
                m_Socket = null;
                m_Mutex.notify();
            }

            if (Thread.currentThread() != m_ReadThread) {
                m_ReadThread.join();
                m_WriteThread.join();
                m_PingThread.interrupt();
                m_PingThread.join();
            }
        }
        catch (Exception e) {
            // do nothing
        }
    }

    public interface EventHandler {
        void onReceiveMessage(TcpConnection connection, String message);
        void onCloseConnection(TcpConnection connection);
    }

    private class ReaderThread extends Thread {

        private final BufferedReader m_Reader;

        private ReaderThread(InputStream inputStream) {
            super("Tcp Connection Stream Reader");
            m_Reader = new BufferedReader(new InputStreamReader(inputStream));
        }

        @Override
        public void run() {
            try {
                String message;
                while ((message = m_Reader.readLine()) != null) {
                    if (!StringUtils.isNullOrWhitespace(message)) {
                        for (EventHandler handler : m_EventHandlers) {
                            handler.onReceiveMessage(TcpConnection.this, message);
                        }
                    }
                }
            }
            catch (SocketException|SocketTimeoutException se) {
                // do nothing; connection closed
            }
            catch (Exception e) {
                e.printStackTrace();
            }
            finally {
                try {
                    m_Reader.close();
                } catch (IOException e) {
                    // do nothing
                }
                finally {
                    close();

                    for (EventHandler handler : m_EventHandlers) {
                        handler.onCloseConnection(TcpConnection.this);
                    }
                }
            }
        }
    }

    private class WriterThread extends Thread {

        private final PrintWriter m_Writer;

        private WriterThread(OutputStream outputStream) {
            super("Tcp Connection Stream Writer");
            m_Writer = new PrintWriter(outputStream);
        }

        @Override
        public void run() {
            try {
                synchronized (m_Mutex) {
                    while (true) {
                        try {
                            if (m_MessageQueue.isEmpty())
                                m_Mutex.wait();

                            if (m_Socket == null || m_Socket.isClosed())
                                break;

                            m_MessageQueue.forEach(m_Writer::println);
                            m_Writer.flush();
                            m_MessageQueue.clear();
                        }
                        catch (InterruptedException ie) {
                            break;
                        }
                    }
                }
            }
            finally {
                m_Writer.close();
            }
        }

    }

    private class PingThread extends Thread {

        private PingThread() {
            super("Tcp Connection Ping");
        }

        @Override
        public void run() {
            while (m_Socket != null && !m_Socket.isClosed()) {
                send("\0");
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }

    }

}
