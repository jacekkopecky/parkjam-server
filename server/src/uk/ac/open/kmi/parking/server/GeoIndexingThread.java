package uk.ac.open.kmi.parking.server;

import org.openrdf.query.QueryLanguage;

import uk.ac.open.kmi.parking.server.Config.MyRepositoryModel;

class GeoIndexingThread extends Thread {

    private static final long DELAY = 60*60*1000; // 60 minutes

    private GeoIndexingThread() {
        // only to be created by itself
    }

    private static GeoIndexingThread thread = null;

    private long targetTime = Long.MAX_VALUE; // end of time
    private boolean nointerrupt;

    @Override
    public void run() {
        // check how long I should sleep, then if I shouldn't, geoindex and sleep forever, else simply sleep
        try {
            while (true) {
                long sleepTime;
                synchronized (this) {
                    sleepTime = this.targetTime - System.currentTimeMillis();
                    if (sleepTime <= 0) {
                        this.targetTime = Long.MAX_VALUE;
                    }
                }

                if (sleepTime <= 0) {
                    synchronized (this) {
                        this.nointerrupt = true;
                        interrupted(); // clear the interrupt state
                    }
                    System.out.println("geoindexing as scheduled at " + System.currentTimeMillis());
                    doGeoindexNow();
                    synchronized (this) {
                        this.nointerrupt = false;
                    }
                } else {
                    try {
                        System.out.println("geoindex thread sleeping for " + sleepTime + " at " + System.currentTimeMillis());
                        sleep(sleepTime);
                    } catch (InterruptedException e) {
                        // ignore, it's all right
                    }
                }
            }
        } catch (RuntimeException e) {
            System.err.println("geoindexing thread ending");
            e.printStackTrace();
            thread = null;
            throw e;
        } catch (Error e) {
            System.err.println("geoindexing thread ending");
            e.printStackTrace();
            thread = null;
            throw e;
        }
    }

    /**
     *  make sure the thread exists and is running
     */
    private static synchronized void startThread() {
        if (thread != null) {
            return;
        } else {
            System.out.println("starting geoindexing thread at " + System.currentTimeMillis());
            thread = new GeoIndexingThread();
            thread.start();
        }
    }

    static void geoindexSometime() {
        startThread();
        synchronized (thread) {
            // if waiting until the end of time, change target time to now + DELAY, interrupt but it won't geoindex immediately
            if (thread.targetTime == Long.MAX_VALUE) {
                thread.targetTime = System.currentTimeMillis() + DELAY;
                System.out.println("scheduling geoindex at " + thread.targetTime);
                if (!thread.nointerrupt) {
                    thread.interrupt();
                }
            } else {
                System.out.println("geoindex already scheduled at " + thread.targetTime + "; now it's " + System.currentTimeMillis());
            }
        }
    }

    static synchronized String geoindexNow() {
        startThread();
        synchronized (thread) {
            // change target time to end of time, interrupt and it won't geoindex immediately
            thread.targetTime = Long.MAX_VALUE;
            if (!thread.nointerrupt) {
                thread.interrupt();
            }
        }
        // do geoindex right now
        System.out.println("geoindex immediately now at " + System.currentTimeMillis());
        return doGeoindexNow();
    }

    private static synchronized String doGeoindexNow() {
        String retval = "";
        MyRepositoryModel repomodel = Config.openRepositoryModel(null);
        String query = "ASK { _:a <http://www.ontotext.com/owlim/geo#createIndex> _:b. }";
        try {
            retval = "geo index rebuild: " + repomodel.getConnection().prepareBooleanQuery(QueryLanguage.SPARQL, query).evaluate() + "\n";
        } catch (Exception e) {
            e.printStackTrace();
            retval = retval + "geo index update failed: " + e.getMessage() + "\n";
        } finally {
            Config.closeRepositoryModel(repomodel);
        }
        return retval;
    }
}
