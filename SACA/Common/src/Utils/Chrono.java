package Utils;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Created by Mike on 7/6/2017.
 */
public class Chrono {

    private long m_TotalElapsedTime;
    private long m_FrameDelta;
    private Instant m_StartTime;
    private Instant m_CurrentTime;
    private Instant m_LastTickTime;

    public Chrono() {
        m_TotalElapsedTime = 0;
        m_FrameDelta = 0;

        m_StartTime = null;
        m_CurrentTime = null;

    }

    public void start() {
        m_StartTime = Instant.now();
        m_CurrentTime = m_StartTime;
    }

    public void tick() {
        m_LastTickTime = m_CurrentTime;
        m_CurrentTime = Instant.now();
        m_TotalElapsedTime = m_StartTime.until(m_CurrentTime, ChronoUnit.MILLIS);
        m_FrameDelta = m_LastTickTime.until(m_CurrentTime, ChronoUnit.MILLIS);
    }

    public long delta() {
        return m_FrameDelta;
    }

    public long totalElapsed() {
        return m_TotalElapsedTime;
    }
}
