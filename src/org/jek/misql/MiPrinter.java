package org.jek.misql;

import java.io.*;

/**
 * @author jek
 *
 */
public class MiPrinter {

	private PrintStream out = null;
	private PrintStream outSave = null;
	private PrintWriter logFile = null;
	private PrintWriter logSave = null;
	private String charSet = "";
	private String logFilename;

	public MiPrinter(String fileName, String csn) {
		charSet = csn;		// Save the out char encoding
		try {
			if (fileName.length() > 0) {
				// TODO : I doubt that this is correct!
				if (csn.length() > 0) {					
					out = new PrintStream(fileName, csn);
				} else {
					// Default encoding
					out = new PrintStream(fileName); 
				}
			} else {
				if (csn.length() > 0) {					
					out = new PrintStream(System.out, true, csn);
				} else {
					// Default encoding
					out = System.out; 
				}
			}
		}
		catch (FileNotFoundException e) {
			System.err.println("MISQL: Failed to open file " + fileName + " for writing.");
			System.exit(1);
		} catch (UnsupportedEncodingException e) {
			System.err.println("MISQL: display_charset " + csn + " is not supported.");
			System.exit(1);
		}
	}
	
	/** Prints the message followed by a newline if there was not one already.
	 * @param message - String to print
	 */
	public void printLine(String message) {
		if (message.endsWith("\n")) {
			print(message);
		} else {
			println(message);
		}		
	}

	public PrintStream printf(String format,Object... args) {
		if (logFile != null) {
			logFile.printf(format, args);
		}
		return out.printf(format, args);
	}

	public void println(String string) {
		if (logFile != null) {
			logFile.println(string);
		}
		out.println(string);
		return; 		
	}

	public void flush() {
		if (logFile != null) {
			logFile.flush();
		}
		out.flush();
		return; 		
	
	}

	public void print(String string) {
		if (logFile != null) {
			logFile.print(string);
		}
		out.print(string);
		return; 		
	}

	public void write(int c) {
		if (logFile != null) {
			logFile.write(c);
		}
		out.write(c);
		return; 		
	}

	public void logClose() {
		if (logFile != null) {
			try {
				logFile.flush();
				logFile.close();
				logFile = null;
			} catch (Exception e) {
				System.err.println("MISQL: Failed to close log file.");
			}			
		}		
	}

	public void logOn(String filename, boolean append) {
		// Setup current output file
		try {
			logClose();		// Close the old logFile (if open)
			logFile = new PrintWriter(
						new BufferedWriter(new FileWriter(filename, append)));
			logFilename = filename;
		} catch (Exception e) {
			System.err.println("MISQL: Failed to open file " + filename + " for writing.");
			// System.exit(1);
		}
	}

	/** Redirects output to file instead of current output file (usually System.out)
	 * @param filename
	 * @param append
	 */
	public void redirectOn(String filename, boolean append) {
		// Setup redirect output file
		try {
			if (outSave != null) {	// Should never happen but just in case
				outSave.flush();
				outSave.close();
				outSave = null;
				System.err.println("MISQL ERROR: redirectOn() outSave not null.");
			}
			outSave = out;	// Save current outputter.
			out = new PrintStream(new FileOutputStream(filename, append));
			logFilename = filename;
		} catch (Exception e) {
			System.err.println("MISQL: Failed to open file " + filename + " for writing.");
			System.exit(1);
		}
	}

	public void redirectOff() {
		if (outSave == null) {	// This should never happen!
			System.err.println("MISQL ERROR: redirectOff() outSave is null!");
			System.exit(1);
		}
		// turn off redirect output file
		if (out != null) {	// Flush and close current redirect.
			out.flush();
			out.close();
			out = null;
		}
		out = outSave;	// Restore previous outputter.
		outSave = null;
	}

	/** Temporarily suspend output to log file<br>
	    Usually to stop prompts being sent.
	 * 
	 */
	public void logSuspend() {
		logSave = logFile;
		logFile = null;
		
	}

	/** Resume output to log file.
	 */
	public void logResume() {
		logFile = logSave;
		logSave = null;
	}

	@Override
	public String toString() {
		if (logFile != null)
			return "logFilename=" + logFilename;
		else
			return "Logging is off";
	}

}
