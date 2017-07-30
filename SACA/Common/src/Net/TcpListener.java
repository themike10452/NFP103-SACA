package Net;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * Created by Mike on 7/10/2017.
 */
public abstract class TcpListener implements Runnable, TcpConnection.EventHandler {

    public final int Port;

    private boolean m_IsRunning;
    private ServerSocket m_Server;
    private Thread m_Thread;

    public TcpListener(int port, int timeout) throws IOException {
        Port = port;

        m_Server = new ServerSocket(Port);
        m_Server.setSoTimeout(timeout);
        m_IsRunning = false;
    }

    public TcpListener(int port) throws IOException {
        this(port, Constants.Integers.SOCKET_TIMEOUT);
    }

    public abstract void onReceiveNewConnection(TcpConnection connection);

    public abstract void onReceiveMessage(TcpConnection connection, String message);

    public abstract void onCloseConnection(TcpConnection connection);

    @Override
    public void run() {
        while (m_IsRunning) {
            try {
                Socket socket = m_Server.accept();

                TcpConnection connection = new TcpConnection(socket, m_Server.getSoTimeout());
                connection.addEventHandler(TcpListener.this);

                onReceiveNewConnection(connection);
            }
            catch (IOException ex) {
                if (!m_IsRunning)
                    break;
            }
        }
    }

    public void startListening() {
        if (m_IsRunning)
            return;

        m_IsRunning = true;
        m_Thread = new Thread(this);
        m_Thread.start();
    }

    public void stopListening() {
        if (!m_IsRunning)
            return;

        try {
            m_IsRunning = false;
            m_Server.close();
            if (Thread.currentThread().getId() != m_Thread.getId()) {
                m_Thread.interrupt();
                m_Thread.join();
            }
        }
        catch (InterruptedException|IOException ie) {
            // do nothing
        }
        finally {
            m_Thread = null;
        }
    }

}
