package uk.ac.open.kmi.parking.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

/**
 * this class logs requests (byte arrays) into a configured file, opening and closing it for every entry to enable log rotation 
 * @author Jacek Kopecky
 *
 */
public class UpdateRequestLogger {
//    private static final ExecutorService executor = Executors.newSingleThreadExecutor();
    private static final File logfile = new File(Config.LOG_FILE_PATH);
    private static final int LOG_SIZE_LIMIT = 65535;
    private static final int BUF_LIMIT = 4096;
    private static final byte[] buffer = new byte[BUF_LIMIT];
    
    /**
     * logs the contents of the input stream into the logfile, up to LOG_SIZE_LIMIT bytes
     * @param operation the label for the log entry, will have the current time appended
     * @param in the input stream whose contents should be logged
     * @throws WebApplicationException if the stream cannot be reset to the beginning
     * @return true if everything went OK, false when a problem was hit such as when the log entry size limit was reached
     */
    public synchronized static boolean log(String operation, InputStream in) throws WebApplicationException {
        boolean retval = true;
        if (!in.markSupported()) {
            new IOException("cannot reset stream, therefore cannot log it - use BufferedInputStream!").printStackTrace();
        }
        in.mark(LOG_SIZE_LIMIT+1);
        FileOutputStream out;
        try {
            if (!logfile.exists()) {
                File parent = logfile.getParentFile().getCanonicalFile();
                if (parent != null && !parent.exists()) {
                    if (!parent.mkdirs()) {
                        throw new IOException("cannot create directories for log file for updates: " + logfile + " (cwd: " + new File(".").getAbsolutePath() + ")");
                    }
                }
                if (!logfile.createNewFile()) {
                    throw new IOException("cannot create log file for updates: " + logfile);
                }
            }
            out = new FileOutputStream(logfile, true);
            out.write('-');
            out.write('-');
            out.write('-');
            out.write(' ');
            out.write(operation.getBytes());
            out.write(' ');
            out.write((new Date()).toString().getBytes());
            out.write('\n');
            
            int readNow, readAll = 0;  
            while (readAll < LOG_SIZE_LIMIT && (readNow = in.read(buffer, 0, Math.min(LOG_SIZE_LIMIT-readAll, BUF_LIMIT))) != -1) {  
                out.write(buffer, 0, readNow);
                readAll += readNow;
            }  
            out.write('\n');
            out.close();
            retval = readAll < LOG_SIZE_LIMIT;
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            retval = false;
        } catch (IOException e) {
            e.printStackTrace();
            retval = false;
        } 
        try {
            in.reset();
        } catch (IOException e) {
            throw new WebApplicationException(
                    Response.status(500).
                    entity("logging error: " + e.getMessage()).
                    type("text/plain").
                    build());
        }
        return retval;
    }
}
