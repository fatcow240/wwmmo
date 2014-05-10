package au.com.codeka.warworlds.server;

import java.sql.ResultSet;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import au.com.codeka.common.model.Simulation;
import au.com.codeka.warworlds.server.ctrl.StarController;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.model.Star;

/**
 * This is a background thread that runs every 2 seconds and simulates
 * the oldest star in the system. This ensures we never let our stars get
 * TOO out-of-date.
 */
public class StarSimulatorThread {
    private static final Logger log = LoggerFactory.getLogger(StarSimulatorThread.class);
    private Thread mThread;
    private boolean mStopped;

    private static int WAIT_TIME_NO_STARS = 10 * 60 * 1000; // 10 minutes, after no stars found
    private static int WAIT_TIME_JUST_SIMULATED = 60 * 60 * 1000; // 1 hour, we just simulated the oldest star
    private static int WAIT_TIME_ERROR = 60 * 1000; // 1 minute, in case of error
    private static int WAIT_TIME_NORMAL = 2 * 1000; // 2 seconds, normal wait time between simulations

    public void start() {
        if (mThread != null) {
            stop();
        }

        mThread = new Thread(new Runnable() {
            @Override
            public void run() {
                threadproc();
            }
        });
        mThread.setDaemon(true);
        mThread.setName("Star-Simulator");
        mThread.setPriority(Thread.NORM_PRIORITY - 1);
        mThread.start();
    }

    public void stop() {
        if (mThread == null) {
            return;
        }

        mStopped = true;
        try {
            mThread.interrupt();
            mThread.join();
        } catch (InterruptedException e) {
            // ignore
        }

        mThread = null;
    }

    private void threadproc() {
        while (!mStopped) {
            int waitTime = simulateOneStar();

            log.info(String.format("Waiting %d seconds before simulating next star.", waitTime / 1000));
            try {
                Thread.sleep(waitTime);
            } catch (InterruptedException e) {
            }
        }
    }

    private int simulateOneStar() {
        try {
            int starID = findOldestStar();
            if (starID == 0) {
                return WAIT_TIME_NO_STARS; 
            }
            log.info("Simulating star: "+starID);

            Star star = new StarController().getStar(starID);
            if (star.getLastSimulation().isAfter(DateTime.now().minusHours(1))) {
                return WAIT_TIME_JUST_SIMULATED;
            }

            new Simulation().simulate(star);
            new StarController().update(star);
            return WAIT_TIME_NORMAL;
        } catch (Exception e) {
            log.info("HERE");
            log.error("Exception caught simulating star!", e);
            // TODO: if there are errors, it'll just keep reporting
            // over and over... probably a good thing because we'll
            // definitely need to fix it!
            return WAIT_TIME_ERROR;
        }
    }

    public static int findOldestStar() throws Exception {
        String sql = "SELECT stars.id FROM stars" +
                    " WHERE empire_count > 0" +
                    " ORDER BY last_simulation ASC LIMIT 1";
        try (SqlStmt stmt = DB.prepare(sql)) {
            ResultSet rs = stmt.select();
            if (rs.next()) {
                return rs.getInt(1);
            }
            return 0;
        }
    }
}
