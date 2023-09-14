package org.jek.misql;




import java.io.*;
import java.sql.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;


import com.sybase.jdbcx.SybConnection;
import com.sybase.jdbc3.jdbc.*;



/**
 * MISQL - Java command line application which extends the original ISQL.
 * 	For documentation on usage, try option --help or command :help; or see
 * 	help() method.
 * 
 * @author jek
 * History:
 *  jek		21 Aug 2008:	Created.
 *  jek		 8 Sep 2011:	Bug fixes: '-w' option not working
 *  						Add maxColWidth :set option.
 *  jek		20 Sep 2011:	Re-wrote MiSQL command line parser.
 */
/**
 * @author jek
 *
 */
public class Misql {
	
	private static final String MISQL_VERSION = "MiSQL v0.3 20 Sep 2011";	
	/**
	 * Holds default connection properties<br>
	 * See jConnect connection docs.
	 */
	static	exProperties _connProps = null;
	
	static	SybDriver sybDriver = null;
	private static	MiPrinter _out = null;
	
	static ArrayList<jConnection> _jConns;
	private static jConnection _curConn;
	
	private static MiHistory	history = null;

	private static int 		_verticalRows = 0;
	private static int		oldVerticalRows = 0;
	private static boolean 	_printHeader = true;
	private static boolean	_printFooter = true;
	private static boolean 	_setEcho = false;
	private static Properties _promptProps = null;
	private static String 	_setCharset = "";
	private static String 	_cmdEnd	= "go";
	private static String 	_defaultDB = "";
	private static String 	_editorCmd = "";
	private static int 		_rowsPerHeader = -1;
	private static String 	_inputFile = "";
	private static String 	_interfacesFile = "";
	private static int 		_loginTimeout = 60;
	private static int 		_errorLevel = 0;
	private static String 	_outputFile = "";
	private static String 	_colTerm = "";
	private static String	_colTermStr = "''";
	private static String	_rowTerm = " \n";
	private static String	_rowTermStr = "' \\n'";
	private static String 	_serverName = "";
	private static int 		_cmdTimeout = 0;
	private static int 		_dispWidth = -1;
	private static int 		_maxColWidth = 512;


	private static boolean	_printPrompt = false;	// Control if prompt is written.
	private static String 	_setPrompt = "@{lineNo}> ";
	private static String   _prompt1Str = "";
	private static String	_prompt1Fmt;		// Store the line 1 prompt printf fmt
	private static String 	_promptStr = "";
	private static String	_promptFmt;			// Stores the prompt printf fmt.


	private static String[]	_colList = null;

	private static int		_errSeverity;			// Severity from last error '-1' if not set.
	private static int 		_contSeverity   = -1;	// Error severity settings.
	private static int 		_promptSeverity = -1;
	private static int 		_exitSeverity   = -1;
	private static int 		_debug = 0;


	/**
	 * @param args
	 * @return via System.exit with 0 for fail or 1 for success.
	 */
	public static void main(String[] args) {
		_connProps = new exProperties();
		_promptProps = new Properties();
		BufferedReader inFile = null;	// Starting default input reader (Usually System.in).		
		
		// First initialise default properties values.
		initProps();
		
		// Instantiate the Sybdriver
        try
        {
            // First try to load the 5.x driver
            sybDriver = (SybDriver) Class.forName(
                "com.sybase.jdbc3.jdbc.SybDriver").newInstance();
            sybDriver.setSybMessageHandler(new  MiMessageHandler());	// Install general message handler
        }
        catch (Exception ex){
        	System.err.println("MISISL: Failure to load SybDriver.");
        	System.err.println("Exception:" + ex);
        	System.exit(1);
        }

		_out = new MiPrinter("", "");		// Init an outputter
		
		// Process command line args
		if (!processCommandline(args))
        {
            System.exit(1);
        }
		
		// Setup current output file
		_out = new MiPrinter(_outputFile, _setCharset);		
		
 		// get the input command stream
        if (_inputFile.length() > 0)
        {
            try
            {
                inFile = new BufferedReader(new FileReader(_inputFile), 4096);
            }
            catch (FileNotFoundException fnfe)
            {
                _out.println("Unable to open " + _inputFile + "\n\t"
                    + fnfe.toString());
                System.exit(1);
            }
        }
        else
        {
            inFile = new BufferedReader(new InputStreamReader(System.in), 4096);
             _printPrompt  = true;		//
             history = new MiHistory(20);
        }
        
         // Initialise the list of known connections.
        _jConns = new ArrayList<jConnection>(); 
		setPrompt(_setPrompt, 0);	// Initialise prompt.
		
		// Process environment variable
		String misqlEnv = System.getenv("MISQL_CMD");
		if (misqlEnv != null && misqlEnv != "") {	// Got a string
			if (_setEcho) {
				_out.println("MISQL_CMD: "+misqlEnv);
			}
//			int start;
//			while((start = misqlEnv.indexOf(':')) != -1) {
//				misqlEnv = doCommand(misqlEnv.substring(start), null);
//			}
			parseCmd(misqlEnv);
		}


		// If -U<user> is given then we need to create a default
		// connection. This needs to be as ISQL defaults!!
		if (_connProps.containsKey("USER")) {
			if (_dispWidth < 0) {			// Display width not set
				_dispWidth = 80;			// ISQL default page width
			}
			
			
			if (! _connProps.containsKey("PASSWORD")) {
				String password = promptForPassword();
				_connProps.put("PASSWORD", password);
			}
			
			// This will set the field _curConn
			openConnection("default", _serverName, _defaultDB);

			// System.out.println("URL is: " + url + ", " + _connProps);
			if (_curConn == null) {		// Failure to connect is fatal here.
				System.exit(1);
			}
		}
		
		if (_dispWidth < 0) {			// Display width not set
			_dispWidth = 9999;			// MiSQL default page width
		}
		
		// Start reading commands from _inFile:
		System.exit(readCommands(inFile));
	}


	
	/** readCommands:<br>
	 * This is the main loop that reads commands from inFile.
	 * It is done as a method as it can recurse if the command is
	 *     :run <runFile>;
	 * @param inFile
	 * @return 0 for success, -1 for failure
	 */
	private static int readCommands(BufferedReader inFile) {
		
		// Set up some instance fields from the _runProps
		// to make life a little easier.
		// NOTE: locale variables will support 'nesting' whereas instance vars remain for the duration.
		// Will need to see which are which.
		// For now, only inFile is nested!
		
		while (true) {		// This loop to read a whole file of batches.
			int	lineNo = 1;
			String line;
			int repeatBatch = 1;
			boolean redirectOn = false;

			StringBuffer batchBuff = new StringBuffer();
			while (true) {	// This loop to read a batch
				// Prompt if reading System.in or echo is on anyway
				if (_printPrompt || _setEcho) {
					if (! _setEcho ) {
						_out.logSuspend();
					}
					printPrompt(lineNo);
					if (! _setEcho ) {
						_out.logResume();
					}				
				}
				
				try {
					line = inFile.readLine();
					if (line == null) {
						// End of file so just return
						inFile.close();
						return 0;
					}
					// Echo line to _out if echo is on
					if (_setEcho) {
						_out.println(line);					
					}
				} catch (Exception ex){
					_out.println("Error reading input file " + inFile.toString());
					// Here if decided to retry
					continue;
				}
				
				lineNo++;
				
				if (line.length() == 0)	// Empty line
				{
					if(_cmdEnd.length() == 0)	// This is special batch end
						break;
					else
						batchBuff.append(line + "\n");  // Place empty line in batch and
						continue;						// Get the netx.
				}				
				
				// Check for ISQL or MISQL commands
				// None of these allow leading white-space so need to ensure
				// this is not the case first:
				if (! Pattern.matches("\\s.*", line)) {
					String[] word = line.split("[; \\t\\x0B\\f\\r]+", 3);	// Split out the first 3 words
															// delimited by white-space and ';'.
					
					// Test for 'quit *' or 'exit *'
					// ISQL compatibility: if first word is quit or exit
					// not mixed upper and lower, and trailing chars ignored.
					if ( word[0].contentEquals("quit") ||
						  word[0].contentEquals("QUIT")) {
						// End of file so just return
						try {
							inFile.close();
						} catch (IOException e) {
							_out.println("Error closing input file " + inFile.toString());
						}
						return 0;
					}
					if ( word[0].contentEquals("exit") || 
						  word[0].contentEquals("EXIT")	) {						
						doCmdClose("");		// Close all connections and
						System.exit(0);		// Exit immediately
					}

					// Check for 'reset *': lower case, no leading white space, ignore trailing
					if (word[0].contentEquals("reset")) {
						batchBuff = new StringBuffer();
						lineNo = 1;
						continue;
					}

					// Is this a 'go' [<n-ttimes>] (aka cmdend) line?
					// Case is ignored
					// This code supports:
					// <cmdEnd> [<n>] [>|>> filename]
					if (word[0].equalsIgnoreCase(_cmdEnd))
					{
						if (word.length > 1) {		// More to look at
							int n = 1;				// Which word to test
							if (word[n].matches("\\d+")) {
								try {
									repeatBatch = Integer.parseInt(word[n]);
								} catch (NumberFormatException nfe) {
									// Ignore non-numerical arg.
									repeatBatch = 1;
								}
								n++;
							}
							if (word.length > n+1 ) {								
								if (word[n].equals(">")) {
									_out.redirectOn(word[n+1], false);		// Open log file for writing
									redirectOn = true;
								} 
								else if (word[n].equals(">>")) {
									_out.redirectOn(word[n+1], true);		// Open log file for append
									redirectOn = true;
								}
							}
						}
						break;				
					}
					
					// Is this a 'vgo' <vertical-rows> (line?
					// Case is ignored
					// This code supports:
					// vgo <n> [>|>> filename]
					if (word[0].contentEquals("vgo"))
					{
						int vRows = 0;
						if (word.length > 1) {		// More to look at
							int n = 1;				// Which word to test
							if (word[n].matches("\\d+")) {
								try {
									vRows = Integer.parseInt(word[n]);
								} catch (NumberFormatException nfe) {
									// Ignore non-numerical arg.
									vRows = 0;
								}
								n++;
							}
							if (word.length > n+1 ) {								
								if (word[n].equals(">")) {
									_out.redirectOn(word[n+1], false);		// Open log file for writing
									redirectOn = true;
								} 
								else if (word[n].equals(">>")) {
									_out.redirectOn(word[n+1], true);		// Open log file for append
									redirectOn = true;
								}
							}
						}
						// If got a valid, temporary vertical row value
						if (vRows > 0) {
							// so save old value and set new one temporarily.
							oldVerticalRows = _verticalRows;
							_verticalRows = vRows;
						}
						break;				
					}
					
					// Support ISQL :r filename into current batch.
					if (word[0].contentEquals(":r") && word.length > 1) {
						// Read contents of ':r <filename>' into batch buffer.
						batchBuff.append(readFile(word[1]));
						continue;
					}
					
					// Test for history commands:
					if (line.charAt(0) == '!' && history != null) {
						StringBuffer batch = history.command(line);
						lineNo--;	// Reset lineNo to keep T-SQL batch correct.
						if (batch != null) {
							// Print out the historical batch
							for (String bLine : batch.toString().split("\n")) {
								printPrompt(lineNo++);
								_out.println(bLine);
								// MISQL commands need to be executed immediately
								if (bLine.charAt(0) == ':') {
									// MISQL commands do not alter current batch.
									parseCmd(bLine);;
									lineNo--;	// Reset lineNo to keep T-SQL batch correct.
									history.add(new StringBuffer(bLine));
								} else {
									batchBuff.append(bLine + "\n");
								}
							}
						} else {
							// Batch not found
							_out.println("History: batch not found.");
						}						
						continue;
					}
					
					// Test for MISQL command - first is ':'
					if (line.charAt(0) == ':') {
						int retCode = parseCmd(line);
						lineNo--;	// Reset lineNo to keep T-SQL batch correct.
						if (_printPrompt && history != null) {
							history.add(new StringBuffer(line));
						}
						continue;
					}
				}			
				
				// Add line to batch and get the next one:
				batchBuff.append(line + "\n");
			}
			
			// Only add to history in interactive mode
			if (_printPrompt && history != null) {
				history.add(batchBuff);
			}
			
			// Got _cmdEnd to execute the batch
			if (batchBuff.toString().length() == 0) // Empty batch so
				continue;
			
			// Here batchBuff contains an language command to be sent to
			// the current connection.
			if (_curConn == null) { // Don't have a valid connection to use
				_out.println("Error: current connection is not open.");
				continue;
			}
			try {
				_errSeverity = -1;	// Reset severity
	            SybStatement stmt = (SybStatement) _curConn.conn.createStatement();
	            // stmt.setEscapeProcessing(_escapeProcessing);
	            // _curConn.conn.clearWarnings();
	            stmt.setQueryTimeout(_cmdTimeout);
	            
	            int batchLoop = repeatBatch;	// Use this to process results
	            boolean retry = false;
	            
				while (batchLoop-- > 0 || retry) {
		            int rowsAffected = 0;
		            retry = false;		// Set to true if batch is to be retried
		            boolean results = stmt.execute(batchBuff.toString());		            
					do {
						SybResultSet rs = null;
						if (results) {
							rs = (SybResultSet) stmt.getResultSet();
								// Print the results to outFile vertical or horizontal format
							if (_verticalRows == 0) {
								Hresults hrs = new Hresults(rs, _dispWidth, _maxColWidth, _colTerm, _rowTerm);	
								if (_printHeader) {
									hrs.printHeader(_out);
								}
								hrs.printRows(_out);
								// hrs.printFooter(_out);
							} else {
								Vresults vrs = new Vresults(rs, _dispWidth, _verticalRows, _maxColWidth, _colTerm, _rowTerm);
								vrs.printRows(_out);
							}
							rowsAffected = stmt.getUpdateCount();
							if (_printFooter) {
								printRowsAffected(rowsAffected);
							}														
						} else {	// Just print rows affected.
							rowsAffected = stmt.getUpdateCount();
							if (_printFooter) {
								printRowsAffected(rowsAffected);
							}																					
						}
						results = stmt.getMoreResults();
					} while (results || rowsAffected  != -1);
					if (_errSeverity > 0) {
						String response = doError(batchBuff);
						if (response.equalsIgnoreCase("e")) {
							doCmdClose("");
							_out.println("MISQL: exiting now.");
							System.exit(_errSeverity);
						} else if (response.equalsIgnoreCase("r")) {
							retry = true;						
						} else if (response.equalsIgnoreCase("q")) {
							// Quit the current inFile so just return
							inFile.close();
							return 0;
						}
						_errSeverity = 0;
					}
				}	// RepeatBatch
	            if (repeatBatch > 1) {
					_out.println(repeatBatch + " xacts:");
				}
	            if (redirectOn) {	// 'go' redirection must be closed now.
					_out.redirectOff();
					redirectOn = false;
				}
	            if (_verticalRows != oldVerticalRows) {	// reset temp. vertical rows from 'vgo' command.
					_verticalRows = oldVerticalRows;
				}
			} 
			catch (SQLException ex) {
				messHandler(ex);
			}
			catch (Exception e) {
				System.err.println("Exception in readCommands: " + e);
				e.printStackTrace();
			}			
		} // Loop forever
	}


	/** Handles the logic of the error severity conditions.<br>
	 * The actions are checked in order:<br>
	 *   exit, prompt<br>
	 *   If current severity is greater or equal to setting then the 'action' will fire.
	 * @return	- The response
	 */
	private static String doError(StringBuffer batch) {
		if (_exitSeverity > 0 && _errSeverity >= _exitSeverity) {
			doCmdClose("");
			_out.println("MISQL: exit severiy excceed - exiting now.");
			System.exit(_errSeverity);
		}
		
		String inLine = "c";
		
		if (_promptSeverity > 0 && _errSeverity >= _promptSeverity) {
			// Display current batch and prompt to retry or not on System.in/out!
			System.out.println("MISQL: Prompt severity exceeded executing batch:");
			System.out.print(batch);
			BufferedReader inFile = new BufferedReader(new InputStreamReader(System.in));
			while (true) {
				System.out.print("     : Enter r-retry,c-continue,q-quit,e-exit :");
				try {
					inLine = inFile.readLine();
					if(inLine == null){
						System.out.println("MISQL error: :onerror prompt got EOF, exiting.");
						System.exit(-1);
					}
					if(inLine.length() > 0)
						break;
				} catch (IOException e) {
					e.printStackTrace();
					System.exit(-1);
				}
			}			
		}
		// Here says continue anyway.
		return inLine;
	}



	private static void printRowsAffected(int rowsAffected) {
		if (rowsAffected > 1 || rowsAffected == 0)
		{
			_out.println("("+rowsAffected + " rows affected)");
		} else if (rowsAffected == 1) {
			_out.println("("+rowsAffected + " row affected)");
		}			
	}


	private static StringBuffer readFile(String line) {
		// TODO This method to read a file into the querybuffer.
		_out.println("*** :r not yet implemented.");
		StringBuffer batchIn = new StringBuffer("");
		return batchIn;
	}

	/** Performs all the processing for the MISQL special commands.<p>
	 * Multiple commands can be on a single line separated by ';'s.<b>
	 * Each commands start with ':' and end with ';' or '\n'.<b>
	 * If 1st character is not ':' then rest of line is ignored as a comment.
	 * 
	 * @param line		The first line of the MISQL command (starts with ':')
	 * @return			The string after the cmd terminator ';'
	 */
	/**
	 * @param line
	 * @return 
	 */
	private static int parseCmd(String line) {
		String whiteSpace = " \t\f\r\n";
		int cmdStart	= 0;		// Start of current command in line (for errors!)
		ArrayList<String> cmdArray = new ArrayList<String>();

		// First loop through the line to produce an array of commands
		// separated by ';'
		StringBuffer token = new StringBuffer();
		int lineLen = line.length();
		int quoteState = 0;					// State on quoted token
											// 0 - none, 1, started, 2 ended
		for (int i = 0; i < lineLen; i++) {
			switch (line.charAt(i)) {
			case ';':	// Got valid command term so execute it
				if (token.length() > 0 || quoteState == 2 )
					cmdArray.add(token.toString());			// Add token
				if (token.length() > 0)
					token.delete(0, token.length());		// Reset token buffer
				quoteState = 0;
				//Here we have a complete MISQL command so convert to an array
				String[] cmd = new String[1];
				cmd = cmdArray.toArray(cmd);
				if(! doCommand(cmd)) {
					// Here and don't recognise the command so print error:
					_out.println("Unrecognised MISQL command: '" + line.substring(cmdStart, i) + "'");
				}
				cmdArray.clear();		// Reset command array
				break;
			
			case '/':		// Escape so add this and next char
				token.append(line.charAt(i++));
				if (i < lineLen)
					token.append(line.charAt(i));
				break;
			
			case '\'':		// Single quoted string
				quoteState = 1;
				while (quoteState == 1 && i++ < lineLen - 1) {
					switch (line.charAt(i)) {
					case '/':		// Escape so add this & next char
						token.append(line.charAt(i++));
						if (i < lineLen)
							token.append(line.charAt(i));
						break;
					case '\'':		// found end of quoted string
						quoteState = 2;
						break;
					default:		// Add char to string
						token.append(line.charAt(i));
					}
				}
				break;
				
			case '\"':		// double quoted string
				quoteState = 1;
				while (quoteState == 1 && i++ < lineLen - 1) {
					switch (line.charAt(i)) {
					case '/':		// Escape so add this & next char
						token.append(line.charAt(i++));
						if (i < lineLen)
							token.append(line.charAt(i));
						break;
					case '\"':		// found end of quoted string
						quoteState = 2;
						break;
					default:		// Add char to string
						token.append(line.charAt(i));
					}
				}
				break;
				
			default:
				if (whiteSpace.indexOf(line.charAt(i)) != -1) {	// Got white space
					// Add the token unless it is empty and this is the first one.
					// This skips leading white-space.
					if (token.length() > 0 || quoteState == 2 )
						cmdArray.add(token.toString());			// Add token
					if (token.length() > 0)
						token.delete(0, token.length());		// Reset token buffer
					quoteState = 0;

					while(i < lineLen && whiteSpace.indexOf(line.charAt(i)) != -1){
						// Skip white space
						i++;
					}
					i--;				// Back up
				} else
					token.append(line.charAt(i));
				break;
			}
		}
		// Got to end of line
		if (quoteState == 1) {	// End of line but unclosed quote!
			_out.println("Unmatched quote: '" + line.substring(cmdStart) + "'");
			return 0;
		}
		
		// Add last token
		if (token.length() > 0 || quoteState == 2 )
			cmdArray.add(token.toString());			// Add token
		if (token.length() > 0)
			token.delete(0, token.length());		// Reset token buffer
		quoteState = 0;
		
		// // Ignore trailing data as comment if it is not a :command
		if (cmdArray.size() == 0 || cmdArray.get(0).charAt(0) != ':')	
			return 0;
		
		String[] cmd = new String[1];
		cmd = cmdArray.toArray(cmd);
		if(! doCommand(cmd)) {
			// Here and don't recognise the command so print error:
			_out.println("Unrecognised MISQL command: '" + line.substring(cmdStart) + "'");
		}
		return 0;
	}

	/** Performs all the processing for the MISQL special commands.<p>
	 * Multiple commands can be on a single line separated by ';'s.<b>
	 * Each commands start with ':' and end with ';' or '\n'.<b>
	 * If 1st character is not ':' then rest of line is ignored as a comment.
	 * 
	 * @param line		The first line of the MISQL command (starts with ':')
	 * @param lineNo 	The starting batch line number. (Not used now and reset to leave batch numbers unchanged.)
	 * @param inFile	The input from from which to read
	 * @return			The string after the cmd terminator ';'
	 */
/*	private static String doCommand(String line, BufferedReader inFile) {
		// This loop needs to read 'inFile' and build up an array of cmd words until
		// ';' is seen. Commands may contain strings in quotes which will be treated as a single word.
		// Any words after ';' (in a line) are ignored (but ';' in a string is ignored!
				
		StringBuffer sb = new StringBuffer(line);		// Keep a copy of the command line for error reporting
		ArrayList<String> cmdArray = new ArrayList<String>();
		String retStr = "";				// The remains after the cmd term ';'
		
		while (true) {	// Keep looping until get terminator
			// First look for quoted string			
			String quote = "\"";
			String part[] = line.split(quote, 3);
			if (part.length == 1) {	// No double quote - try single
				quote = "'";
				part = line.split(quote, 3);				
			}
			if (part.length == 1) {	// Quote string not found
				quote = "";			// Use this as a flag
			}
			
			// Here part[0] hold command up to first quote
			// part[1] _may_ have a part or whole quoted string
			// part[2] may have everything after the second quote
			
			String splitTerm[] = part[0].split(";", 2);
			if(splitTerm.length == 2){	// Got terminator so add those to cmd[]
				for (String w : splitTerm[0].split("[ ,\\t\\x0B\\f\\r\\n]+")) {
					if (w.length() > 0) {
						cmdArray.add(w);
					}				
				}
				retStr = splitTerm[1];	// Everything after ';'
				break;
			}
			
			// Here not found ';' in part[0] so split and add it to cmd
			for (String w : part[0].split("[ ,\\t\\x0B\\f\\r\\n]+")) {
				if (w.length() > 0) {
					cmdArray.add(w);
				}				
			}
			
			if (part.length == 1) {	//There was only 1 part and no term so
			*//*** Got to end of line.
				if(inFile == null){	// Reading commands from variable
					return "";		// No command term, no way to complete it!
				}
				printPrompt(0);		// get another line
				try {
					line = inFile.readLine();
				} catch (Exception ex){
					onError("Error reading input file " + inFile.toString());
					// Here if decided to retry
					continue;
				}				
				if (line == null) {
					// End of file so just return
					return "";
				}
				sb.append(line);
				continue;
			****//*
				break;
			}
			
			if (part.length == 2) {		// part[1] contains first part of quoted string
				onError("Error: unmatched " + quote	);
				// ToDo: could read in multi-line string here but is that confusing?
				return part[1];			// Return part quoted string
			}			
					
			if (part.length == 3) {		// part[1] contains a quoted string
				cmdArray.add(part[1]);
				line = part[2];			// part[2] contains the rest so loop around to process it
				continue;
			}
		}
		
		//Here we have a complete MISQL command so convert to an array
		// The last cmd word is "" (for the ';')
		String[] cmd = new String[1];
		cmd = cmdArray.toArray(cmd);*/
	
	private static boolean doCommand(String[] cmd) {		
		// Got a command so branch off to each command handler method
		boolean ok = false;
		while (cmd.length > 0) {
			if (cmd[0].equalsIgnoreCase(":") ||
					cmd[0].equalsIgnoreCase(":#")) {	// Just a comment
				ok = true;
				break;
			}
			
			// Print history
			if (cmd[0].equalsIgnoreCase(":history")
					|| cmd[0].equalsIgnoreCase(":h")) {
				history.print(_out);
				ok = true;
				break;
			}
			
			if (cmd[0].equalsIgnoreCase(":open")) {
				ok = doCmdOpen(cmd);
				break;
			}
			if (cmd[0].equalsIgnoreCase(":on")) {
				if (cmd.length == 2) {	// Switch to connection
					for (jConnection jc : _jConns) {
						if (jc.connName.equals(cmd[1])) {
							_curConn = jc;
							ok = true;
							return ok;
						}
					}
					_out.println("Connection '" + cmd[1] + "' has not been found.");
					ok = true;	// Not an error as such.
				}
				break;
			}
			if (cmd[0].equalsIgnoreCase(":close")) {
				if (cmd.length == 2) {	// Close the connection.
					doCmdClose(cmd[1]);
					ok = true;
				}
				break;
			}
			if (cmd[0].equalsIgnoreCase(":pause")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":run")) {
				if (cmd.length == 2) {	// Execute the new file.
					doCmdRun(cmd[1]);
					ok = true;
				}
				break;
			}
			if (cmd[0].equalsIgnoreCase(":log")) {
				if (cmd.length == 3) { // :log on|append filename;
					if (cmd[1].equalsIgnoreCase("on")) {
						_out.logOn(cmd[2], false);
						ok = true;
						break;
					}
					if (cmd[1].equalsIgnoreCase("append")) {
						_out.logOn(cmd[2], true);
						ok = true;
						break;
					}
				}
				if (cmd.length == 2 &&		// :log close;
						(cmd[1].equalsIgnoreCase("off") ||
						 cmd[1].equalsIgnoreCase("close"))) {
					_out.logClose();
					ok = true;
				}
				break;
			}
			if (cmd[0].equalsIgnoreCase(":set")) {
				ok = doCmdSet(cmd);
				break;
			}
			if (cmd[0].equalsIgnoreCase(":onerror")) {
				if (cmd.length == 3) {  // :onerror <#sev> command
					ok = doCmdOnError(cmd[1], cmd[2]);
				}else{
					ok = doCmdOnError("", "");	// :onerror report current
				}
				break;
			}
			if (cmd[0].equalsIgnoreCase(":print")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":status")) {
				ok = doCmdStatus();
				break;
			}
			if (cmd[0].equalsIgnoreCase(":help")) {
				if (cmd.length == 2) {
					printHelp(cmd[1]);
				} else {
					printHelp("");
				}
				ok = true;
				break;
			}
			break;			
		}
		return ok;		// Return the remaining line to caller.		
	}

	/** Report current status of connections and log etc.
	 * @return  - true unless there is a problem.
	 */
	private static boolean doCmdStatus() {
		_out.println("status:-");
		
		ArrayList<jConnection> allConns = new ArrayList<jConnection>(_jConns);
		
		_out.println("  Connections:");

		for (jConnection jc : allConns) {
			_out.println("    " + jc.connName + ": on " + jc.server + " as '" + jc.userName + "'");
		}
		
		// Logging status
		_out.println("  Logging: " + _out.toString());
		
		// Version
		_out.println("  Version: " + MISQL_VERSION);
		return true;
	}



	/** Set the severity:action.<br>
	 * 
	 * @param severityValue
	 * @param action
	 */
	private static boolean doCmdOnError(String severityValue, String action) {
		if (severityValue.length() == 0) { // Just report current settings
			_out.println("onerror:-");
			_out.println("    " + ((_exitSeverity < 0) ? "not set" : _exitSeverity) + " - exit");
			_out.println("    " + ((_promptSeverity < 0) ? "not set" : _promptSeverity) + " - prompt");
			return true;
		}
		
		int sevNum;
		if (severityValue.matches("\\d+")) {
			try {
				sevNum = Integer.parseInt(severityValue);
			} catch (NumberFormatException nfe) {
				return false;
			}
			if (action.equalsIgnoreCase("prompt")) {
				_promptSeverity = sevNum;
				return true;
			} else if (action.equalsIgnoreCase("exit")) {
				_exitSeverity = sevNum;
				return true;				
			}
		}
		return false;
	}



	/** Close the named connection and drop it from the list.<br>
	 * If connName is empty, close all the connections in the list.
	 * @param connName
	 */
	private static void doCmdClose(String connName) {
		
		CopyOnWriteArrayList<jConnection> allConns = new CopyOnWriteArrayList<jConnection>(_jConns);
		
		for (jConnection jc : allConns) {
			if (connName.length() == 0 ||			// If closing all connections or
					jc.connName.equals(connName)) {	// Found this conn by name
				try {
					jc.conn.close();
				} catch (SQLException sqe) {
					messHandler(sqe);
				}
				
				// If this was the current connection - reset it to null
				if (_curConn != null &&
					_curConn.connName.equals(jc.connName)) {
					_curConn = null;
				}
				// Remove the connection from the list anyway
				_jConns.remove(jc);
				
				if (connName.length() != 0 ||
						_jConns.isEmpty()) {
					return;
				}
			}
		}
		// Here if scanned all connections but didn't find name.
		if (connName.length() != 0) {
			_out.println("Connection '" + connName + "' has not been found.");		
		}
	}



	/** Open the given filename and executes the commands there in.
	 * @param inFile 
	 * @param script file to execute
	 */
	private static void doCmdRun(String fileName) {
		BufferedReader inFile;
		
		try
		{
			inFile = new BufferedReader(new FileReader(fileName));
		}
		catch (FileNotFoundException fnfe)
		{
			_out.println("Unable to open " + fileName + "\n\t"
					+ fnfe.toString());
			return;
		}
		// Execute the commands as if typed!
		boolean savePrompt = _printPrompt;
		_printPrompt = false;			// Turn off prompts for duration
		readCommands(inFile);
		_printPrompt = savePrompt;		// Restore printPrompt setting.
		return;
	}



	/** Process ':open' commands<p>
	 * Syntax is :open <connName> to <server> as <user> [using <password>] | [prompt]
	 * @param cmd - array of command words
	 */
	private static boolean doCmdOpen(String[] cmd) {
		// cmd[0] is ':open'
		// cmd[1-n] are these args
		while (cmd.length >= 7) {
			String connName = cmd[1];
			for (jConnection jc : _jConns) {
				if (jc.connName.equals(connName)) {
					_out.println(":open connection '"+connName+"' already exists.");
					return false;
				}			
			}
			if (! cmd[2].equalsIgnoreCase("to")) {
				break; // Error!
			}
			String serverName = cmd[3];
			if (! cmd[4].equalsIgnoreCase("as")) {
				break; // Error!
			}

			_connProps.put("USER", cmd[5]);
			String password = "";
			if (cmd[6].equalsIgnoreCase("using")) {
				if (!(cmd.length == 8)) {	// No password!
					break; // Error!
				}
				password = cmd[7];			
			} else if (cmd[6].equalsIgnoreCase("prompt")) {
				password = promptForPassword();
			} else {
				break; // Not 'using' or 'prompt'
			}
			_connProps.put("PASSWORD", password);
			
			// Here we have all the values to open the new Connection. (no defDB)
			openConnection(connName, serverName, "");	
			return true;
		}
		// Here with invalid :set command
		_out.println(":open syntax error.");		
		return false;
	}



	private static void openConnection(String connName, String serverName, String defDB) {
		SybConnection conn = null;
		String url = "";
		String[] urlArray = null;
		
		// How to use interfaces file in jConnect:
		// String url = "jdbc:sybase:jndi:file://D:/syb1252/ini/mysql.ini?myaseISO1”
		if (serverName.indexOf(':') != -1) { // Assume this is a JDBC url as hostname:port
			url="jdbc:sybase:Tds:" + serverName;
			// Split server name into parts: host, port, defdb, connection options
			urlArray = serverName.split("[:/?]");
		} else {
			url = "jdbc:sybase:jndi:file://" + _interfacesFile + "?" + serverName;
			// _out.println("URL is [" + url + "]");
		}
		
		
		
		try {
			DriverManager.setLoginTimeout(_loginTimeout);
			// System.out.println("_connProps is ["+_connProps+"]");
			conn = (SybConnection)DriverManager.getConnection(url, _connProps);
			_curConn = new jConnection(conn, connName);
			_curConn.userName = _connProps.getProperty("USER");
			_curConn.server = serverName;
			_serverName = serverName;
			messHandler(conn.getWarnings());
			conn.clearWarnings();
			// If using URL 'host:port/defdb'
			// then need to initialise database name in conn object.
			// For some reason 'master' is returned even though it is incorrect!
			if (urlArray != null && urlArray.length >= 3)
				_curConn.dbName = urlArray[2];
			_jConns.add(_curConn);
			
			// If a default database try to 'use' it
			if(defDB.length() > 0) {
				SybStatement stmt = (SybStatement)conn.createStatement();
				stmt.executeUpdate("use "+defDB);
			}
		}
		catch(SQLException ex) {
			messHandler(ex);
		}
		return;		
	}


	/** Process all ':set' commands
	 * @param cmd - array of command words
	 */
	private static boolean doCmdSet(String[] cmd) {
		// cmd[0] is ':set'
		// cmd[1-n] are the args
		if (cmd.length == 1) {	// Just ':set' so report current settings.
			_out.println("set:-");
			_out.println("         echo(EC)= " + (_setEcho ? "on" : "off"));
			_out.println("       header(HD)= " + (_printHeader ? "on" : "off"));
			_out.println("       footer(FT)= " + (_printFooter ? "on" : "off"));
			_out.println("      colTerm(CT)= " + ((_colTerm.length()==0) ? "off" : _colTermStr));
			_out.println("      rowTerm(RT)= " + ((_rowTerm.length()==0) ? "off" : _rowTermStr));
			_out.println("    dispWidth(DW)= " + _dispWidth);
			_out.println("  maxColWidth(CW)= " + _maxColWidth);
			_out.println(" verticalRows(VR)= " + ((_verticalRows == 0) ? "off" : _verticalRows));
			_out.println("      prompt1(P1)= " + ((_prompt1Str.length() == 0) ? "off" : "'"+_prompt1Str+"'"));
			_out.println("       prompt(PR)= " + ((_promptStr.length() == 0) ? "off" : "'"+_promptStr+"'"));
			_out.println("      colList(CL)= " + ((_colList == null) ? "all" : _colList ));
			_out.println("      history(HI)= " + ((history == null) ? "off" : history.getMaxsize()));
			return true;
		}
		
		if (cmd.length == 3) {
			if (cmd[1].equalsIgnoreCase("dispWidth") ||
					cmd[1].equalsIgnoreCase("DW")) {
				if (cmd[2].matches("\\d+")) {
					try {
						_dispWidth = Integer.parseInt(cmd[2]);
					} catch (NumberFormatException nfe) {
						return false;
					}
					return true;
				}
				return false;
			}
			
			if (cmd[1].equalsIgnoreCase("maxColWidth") ||
					cmd[1].equalsIgnoreCase("CW")) {
				if (cmd[2].matches("\\d+")) {
					try {
						_maxColWidth = Integer.parseInt(cmd[2]);
					} catch (NumberFormatException nfe) {
						return false;
					}
					return true;
				}
				return false;
			}
			
			if (cmd[1].equalsIgnoreCase("verticalRows") ||
					cmd[1].equalsIgnoreCase("VR")) {
				if (cmd[2].equalsIgnoreCase("off")) {
					_verticalRows = 0;
					return true;
				}
				if (cmd[2].matches("\\d+")) {
					try {
						_verticalRows = Integer.parseInt(cmd[2]);
					} catch (NumberFormatException nfe) {
						// Ignore non-numerical arg.
						_verticalRows = 0;
						return false;
					}
					return true;
				}
				return false;
			}
			// Set echo output on or off
			if (cmd[1].equalsIgnoreCase("echo") ||
					cmd[1].equalsIgnoreCase("EC")) {
				if (cmd[2].equalsIgnoreCase("on")) {
					_setEcho = true;
					return true;
				}
				if (cmd[2].equalsIgnoreCase("off")) {
					_setEcho = false;
					return true;
				}
				_out.println(":set echo on|off");
				return false;
			}
			// Set header output on or off
			if (cmd[1].equalsIgnoreCase("header") ||
					cmd[1].equalsIgnoreCase("HD")) {
				if (cmd[2].equalsIgnoreCase("on")) {
					_printHeader = true;
					return true;
				}
				if (cmd[2].equalsIgnoreCase("off")) {
					_printHeader = false;
					return true;
				}
				_out.println(":set header on|off");
				return false;
			}
			// Set footer output on or off
			if (cmd[1].equalsIgnoreCase("footer") ||
					cmd[1].equalsIgnoreCase("FT")) {
				if (cmd[2].equalsIgnoreCase("on")) {
					_printFooter = true;
					return true;
				}
				if (cmd[2].equalsIgnoreCase("off")) {
					_printFooter = false;
					return true;
				}
				_out.println(":set footer on|off");
				return false;
			}
			
			// Set column terminator
			if (cmd[1].equalsIgnoreCase("colTerm") ||
					cmd[1].equalsIgnoreCase("CT")) {
				if (cmd[2].equalsIgnoreCase("default")) {
					_colTermStr = "''";
					_colTerm = "";
				}
				else {
					_colTermStr = cmd[2];
					_colTerm = Utils.escape(cmd[2]);
				}
				return true;
			}	
					
			// Set row terminator
			if (cmd[1].equalsIgnoreCase("rowTerm") ||
					cmd[1].equalsIgnoreCase("RT")) {
				if (cmd[2].equalsIgnoreCase("default")) {
					_rowTermStr = "''";
					_rowTerm = "";
				}
				else {
					_rowTermStr = cmd[2];
					_rowTerm = Utils.escape(cmd[2]);
				}
				return true;
			}	

			// Set prompt
			if (cmd[1].equalsIgnoreCase("prompt") ||
					cmd[1].equalsIgnoreCase("PR")) {
				setPrompt(cmd[2], 0);
				return true;
			}
			// Set prompt1
			if (cmd[1].equalsIgnoreCase("prompt1") ||
					cmd[1].equalsIgnoreCase("P1")) {
				setPrompt(cmd[2], 1);
				return true;
			}	
		}
		
		if (cmd.length >= 3) { 
			// Add colList names or numbers to _colList[]
			if (cmd[1].equalsIgnoreCase("colList") ||
					cmd[1].equalsIgnoreCase("CL")) {
				if (cmd.length == 3 && cmd[2].equalsIgnoreCase("all")) {
					_colList = null;
				}
				_colList = new String[cmd.length - 2];
				for (int i = 0; i < _colList.length; i++) {
					_colList[i] = cmd[i+2];
				}
				return true;
			}
		}
		if (cmd[1].equalsIgnoreCase("history") ||
				cmd[1].equalsIgnoreCase("HI")) {
			if (cmd[2].equalsIgnoreCase("off")) {
				history = null;
				return true;
			}
			if (cmd[2].matches("\\d+")) {
				try {
					if (history == null) {
						history = new MiHistory(Integer.parseInt(cmd[2]));
					} else {
						history.setMaxsize(Integer.parseInt(cmd[2]));	
					}
				} catch (NumberFormatException nfe) {
					// Ignore non-numerical arg.
					return false;
				}
				return true;
			}
			return false;
		}
	
		// Here with invalid :set command
		_out.println(":set syntax error.");		
		return false;		
	}


	/** prints the prompt<p>
	 * Note that the printf args MUST be strings in the same order as the PROMPTVAR properties.<br>
	 * Any additional variables must be added to both PROMPTVAR and to the printf() args.
	 * @param lineNo
	 */
	private static void printPrompt(int lineNo) {
		String lineStr = String.valueOf(lineNo);
		String batchStr = String.valueOf(history.getLastBatch()+1);		
		Date now = new Date(System.currentTimeMillis());
		SimpleDateFormat timeformat = new SimpleDateFormat("HH:mm:ss.SSS");
		String timeStr = timeformat.format(now);
		
		if (lineNo == 0) {	// Special case :cmd prompt
			_out.printf(": ");
			_out.flush();
			return;
		}
		if (_curConn == null) {	// No current connection
			_out.printf("No active connection: ");
			_out.flush();
			return;
		}
		
		// If promt1 is set and this is the first line in the batch
		if (lineNo == 1 && _prompt1Fmt != null && _prompt1Fmt.length() > 0) {
			_out.printf(_prompt1Fmt, lineStr, batchStr, _curConn.dbName, _curConn.connName, 
					_curConn.userName, _curConn.server, timeStr);
		} else {
			_out.printf(_promptFmt, lineStr, batchStr, _curConn.dbName, _curConn.connName, 
					_curConn.userName, _curConn.server, timeStr);
		}
		_out.flush();		
	}	
	/* These strings MUST match, in order, to the args in the printPrompt printf() call
	 */
	private static final String PROMPTVAR[] = {
		"lineNo",
		"batchNo",
		"dbName",
		"connName",
		"userName",
		"server",
		"time"
	};

	/** parses command String to generate printf style prompt string.
	 * @param cmdStr - free txt including variables:<br>
	 * @param whichPromt - 1 for prompt1 uses for the begining of a batch.
	 * @{lineNo} @{dbName} @{connName} @{userName} @{server}
	 */
	private static void setPrompt(String cmdStr, int whichPrompt) {
		String fmtStr = "";
		String pStr = cmdStr;
		cmdStr = Utils.escape(cmdStr);	// Escape any %, \n, \t chars!
				
		while (true) {
			String word[] = cmdStr.split("@\\{", 2);
			if (word.length == 1) { // No variables
				fmtStr = fmtStr + word[0];
				break;				// All done.
			}
			
			fmtStr = fmtStr + word[0];
			cmdStr = word[1];
			word = cmdStr.split("}", 2); // Find variable terminator
			if (word.length == 1) { // No terminator
				fmtStr = fmtStr + "@{" + word[0];
				break;				// All done.
			}
			
			// Here word[0] contains a variable, word[1] the rest
			cmdStr = word[1];
			String pFmt = _promptProps.getProperty(word[0]);
			if (pFmt == null) {	// This variable name is NOT found/supported
				// so just add it as a literal
				fmtStr = fmtStr + "@{" + word[0] + "}";				
			} else {	// Embed the pFmt into format string
				fmtStr = fmtStr + pFmt;
			}
		}
		// If this is :set prompt1 ...;
		if (whichPrompt == 1) {
			_prompt1Str = pStr;
			_prompt1Fmt = fmtStr;
			return;
		}
		// else set the normal prompt format
		_promptStr = pStr;
		_promptFmt = fmtStr;
	}

	/** promptForPassword:<p>
	 * Prompts for the password from the input file.
	 * @return the password as a string
	 */
	private static String promptForPassword() {
		try {
			if ( _printPrompt )	{	// Using stdin
				return String.valueOf(PasswordField.getPassword(System.in, "Password: "));
			}
		}
		catch (NullPointerException npe) { // Treat NPE as empty string
			return ("");
		}
		catch (Exception ex) {
			System.err.println("Failed to read password from input file "
					+ System.in.toString());
		}
		return null;
	}

	/** Set up default properties for both<br>
	 *  _runProps: runtime properties and
	 *  _connProps: jConnect default properties
	 * 
	 */
	private static void initProps() {
		// Construct default iFile value:
		String sybaseEnv = System.getenv("SYBASE");
		if (sybaseEnv == null)
			sybaseEnv = "";
		String iFile = "";
		if (System.getProperty("os.name").contains("Windows")) {
			iFile = sybaseEnv + "\\ini\\sql.ini";
		} else {
			iFile = sybaseEnv + "/interfaces";
		}
		_interfacesFile = iFile;
		// Construct default server name
		if ( (_serverName = System.getenv("DSQUERY")) == null){
			_serverName = "SYBASE";
		}
				
		_connProps.setProperty("APPLICATIONNAME", "MISQL");
		String hostname = System.getenv("HOST");	// Set HOSTNAME is HOST is set
		if (hostname != null)
			_connProps.setProperty("HOSTNAME", hostname);
		_connProps.setProperty("SERVER_INITIATED_TRANSACTIONS", "False"); // Needs begin/commit commands	
		
		// Ignore DONEINPROC results
		_connProps.setProperty("IGNORE_DONE_IN_PROC", "True");
		// Try to stop metadata stuff
		_connProps.setProperty("USE_METADATA", "False");
		
		// My 'special' in Tds.java
		_connProps.setProperty("FILTER_INFO_MSG", "False");
		
		// To stop throwing
		// java.sql.SQLException: JZ0S8: An escape sequence in a SQL Query was malformed:
		_connProps.setProperty("ESCAPE_PROCESSING_DEFAULT", "False");
		
		// Initialise prompt variable name to printf %fmt $position
		for (int i = 0; i < PROMPTVAR.length; i++) {
			_promptProps.put(PROMPTVAR[i], "%"+(i+1)+"$s");		
		}
	}

	/**
	 * processCommandline: Process the command line args into Properties<p>
	 *  This code has been largely lifted from IsqlApp sample
	 *  and modified to match current ISQL command line functionality.<br>
	 *  ISQL supports:<br>
	 *  isql [-b] [-e] [-F] [-p] [-n] [-v] [-X] [-Y] [-Q]
		    [-a display_charset]
		    [-A packet_size]
		    [-c cmdend]
		    [-D database]
		    [-E editor] // Not implemented.
		    [-h header]
		    [-H hostname]
		    [-i inputfile]
		    [-I interfaces_file]
		    [-J client_charset]
		    [-K keytab_file]
		    [-l login_timeout]
		    [-m errorlevel]
		    [-o outputfile]
		    [-P password]
		    [-R remote_server_principal]
		    [-s colseparator]
		    [-S server_name]
		    [-t timeout]
		    -U username
		    [-V [security_options]]
		    [-w columnwidth]
		    [-z locale_name]
		    [-Z security_mechanism]
	 *  @param    args     Array of command line arguments
	 *  @return            True if successful,False if invalid argument found.
	 */
	static private boolean processCommandline(String args[])
	{
		int errorCount = 0;

		Properties argProps = Utils.args2Props(args);
		
		// System.out.println("processCommandLine: argProps is: " + argProps.toString());
		
		Enumeration e = argProps.propertyNames();

		while (e.hasMoreElements())
		{
			String option = (String) e.nextElement();
			String optValue = argProps.getProperty(option);
			
			if (option.length() == 1)	// Single letter option so can switch
			{
				switch(option.charAt(0))
				{
					// [-b] [-e] [-F] [-p] [-n] [-v] [-X] [-Y] [-Q]
				case 'b':
					_printHeader = false;
					break;

				case 'e':
					_setEcho = true;
					break;

				case 'n':
					_setPrompt = "";
					break;

				case 'F':
					// TODO Turn off FIPS flagger (not hard!)
					System.err.println("MISQL: Warning: -F (FIPS flagger) not implemented yet.");
					break;

				case 'p':
					// TODO : Add performance stats - need to check ISQL
					System.err.println("MISQL: Warning: -p (performance statisics) not implemented yet.");
					break;

				case 'v':
					printVersion();
					return(false);

				case 'X':
					_connProps.setProperty("ENCRYPT_PASSWORD", "True");
					break;

				case 'Y':
					_connProps.setProperty("SERVER_INITIATED_TRANSACTIONS", "True");
					break;

				case 'Q':
					// TODO : Add HA support - should be possible in jConnect
					System.err.println("MISQL: Warning: -Q (HA failover) not implemented yet.");
					break;					

					// Now process arguments that take values
				case 'a':
					// TODO  : Not sure how this is done.
					_setCharset = argProps.getProperty(option);
					System.err.println("MISQL: Warning: -a display_charset not implemented yet.");						
					break;
				case 'A':
					if ( optValue.length() > 0)
						_connProps.setProperty("PACKETSIZE", optValue);
					else
						errorCount++;
					break;
				case 'c':
					_cmdEnd = optValue;
					break;
				case 'D':
					if ( optValue.length() > 0) {
						_defaultDB = optValue;
						_connProps.setProperty("SERVICENAME", optValue);
					}
					else
						errorCount++;
					break;
				case 'E':
					// TODO : Probably will not implement this.
					System.err.println("MISQL: Warning: -E editor not implemented yet.");						
					if ( optValue.length() > 0)
						_editorCmd = optValue;
					else
						errorCount++;
					break;
				case 'h':   // -h headers: specifies the number of rows to print 
							//between column headings. The default prints headings 
							//only once for each set of query results.
					try {
						_rowsPerHeader = Integer.parseInt(optValue);
					} catch (NumberFormatException nfe) {
						errorCount++;
					}
					break;
				case 'H':
					_connProps.setProperty("HOSTNAME", optValue);
					break;
				case 'i':
					if ( optValue.length() > 0)
						_inputFile = optValue;
					else
						errorCount++;
					break;
				case 'I':
					if ( optValue.length() > 0)
						_interfacesFile = optValue;
					else
						errorCount++;
					break;
				case 'J':
					_connProps.setProperty("CHARSET", optValue);
					break;
				case 'K':
					// TODO : Kerberos and security - tricky!
					System.err.println("MISQL: Warning: -K keytab_file not implemented yet.");						
					break;
				case 'l':
					try {
						_loginTimeout = Integer.parseInt(optValue);
					} catch (NumberFormatException nfe) {
						errorCount++;
					}
					break;
				case 'm':
					try {
						_errorLevel = Integer.parseInt(optValue);
					} catch (NumberFormatException nfe) {
						errorCount++;
					}
					break;
				case 'o':
					if ( optValue.length() > 0)
						_outputFile = optValue;
					else
						errorCount++;
					break;
				case 'P':
					_connProps.setProperty("PASSWORD", optValue);
					break;
				case 'R':
					// TODO : More Kerberos and security
					System.err.println("MISQL: Warning: -R remote_server_principal not implemented yet.");						
					break;
				case 's':
					_colTerm = optValue;
					break;
				case 'S':
					if ( optValue.length() > 0)
						_serverName = optValue;
					else
						errorCount++;
					break;
				case 't':
					try {
						_cmdTimeout = Integer.parseInt(optValue);
					} catch (NumberFormatException nfe) {
						errorCount++;
					}
					break;
				case 'U':
					_connProps.setProperty("USER", optValue);
					break;
				case 'V':
					// TODO : More security options
					System.err.println("MISQL: Warning: -V security_options not implemented yet.");						
					break;
				case 'w':
					try {
						_dispWidth = Integer.parseInt(optValue);
					} catch (NumberFormatException nfe) {
						errorCount++;
					}
					break;
				case 'z':
					_connProps.setProperty("LANGUAGE", optValue);
					break;
				case 'Z':
					// TODO : More security options
					System.err.println("MISQL: Warning: -Z security_mechanism not implemented yet.");						
					break;

				default:
					System.out.println("Invalid command line option: -" + option);
					printHelp("usage");
					errorCount++;
					break;
				}
				if (errorCount > 0) {
					System.out.println("Invalid command line arg: [" 
							+ optValue + "] for option: -" + option + "");
				}
			}
			else
			{	
				if (option.equals("args")) // Ignore args without options.
					continue;
				
				// Here for arguments of form --option[=value]
				if (option.equals("help")) {
					printHelp("usage");
					return false;
				}
				if (option.equals("debug")) {
					try {
						_debug = Integer.parseInt(optValue);
					} catch (NumberFormatException nfe) {
						errorCount++;
					}
					continue;
				}
				System.out.println("Invalid command line arg: --" + option);
				errorCount++;
			}
		}
		return(errorCount == 0);
	}
	   


	/** prints file from doc/help-<topic>.txt from the archive.<p>
	 * If the help-<topic> does not exist, prints a generic message.
	 * @param topic
	 */
	private static void printHelp(String topic) {
		String helpFileName = "help-"+ topic +".txt";
		InputStream helpFile = Misql.class.getClassLoader().getResourceAsStream(helpFileName);
		if (helpFile == null) {
			if (topic.length() > 0) {
				_out.println("No help available on '"+topic+"'");
			}
			_out.println("Help is available on a number of topics. Try ':help help;' for details.");
			return;			
		}
		// Write out the helpFile to the output
		try {
			int c;
			while ((c = helpFile.read()) != -1)
				_out.write(c);
			helpFile.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return;
	}

	private static void printVersion() {
		// TODO Auto-generated method stub	
		_out.println(MISQL_VERSION);
		System.exit(0);
	}
	
	/** General Server message handler <br>
	 * should produce same error message
	 * format as ISQL eg:<p>
	 * Msg 911, Level 11, State 2:
     * Server 'JEKXP1502', Line 1:
     * Attempt to locate entry in sysdatabases for database 'nodb' by name failed - no entry found under that name. Make sure that name is entered properly.
	 * <p>
	 * If this is NOT a SybSQLException or no EEDinfo is present then this returns the SQLException
	 * else if return null so jConnect ignores the message.
 	 * @author jek
	 *
	 */	
	public static SQLException messHandler(SQLException sqe) {
		SQLException notaSybE = null;
		boolean handled;		// True if exception has been processed.
		while (sqe != null)
		{
			handled = false;
			
			if (sqe instanceof SybSQLWarning ||
					sqe instanceof SQLWarning) {	// Check this is a SybSQLWarning
//				_out.println("Warning " + sybw.getErrorCode() + 
//						", Level " + sybw.getSeverity() + 
//						", State " + sybw.getState() + ":");
//				_out.println("Server '" + sybw.getServerName() + 
//						"', Line " + sybw.getLineNumber() + ":");
				switch (sqe.getErrorCode()) {
				case 5704:	// Skip "Changed client character set setting"					
					break;
				
				case 5701:	// Handle "Changed database context to 'master'"
					String msg[] = sqe.getMessage().split("'");
					if (msg.length >= 2) {
						_curConn.dbName = msg[1];
					}
					break;

				default:
					// Skip some JDBC metadata warnings (to work with RS etc).
					String SQLstate = sqe.getSQLState();
					if (SQLstate != null && 
							  (sqe.getSQLState().equalsIgnoreCase("010TQ") // Server charset
							|| sqe.getSQLState().equalsIgnoreCase("010MX")) // Metadata accessor
							)
						break;
				
/*					_out.println("SQLWarning : " +
							"SqlState: " + sqe.getSQLState()  +
							" " + sqe.toString() +
							", ErrorCode: " + sqe.getErrorCode());
*/		
					// _out.print(sqe.getMessage());
					_out.printLine(sqe.getMessage());
					handled = true;
					break;
				}
				
			} else if (sqe instanceof SybSQLException) {	// Check this is a SybSQLException
				SybSQLException sybe = (SybSQLException) sqe;
				_errSeverity = sybe.getSeverity();
				if (_errorLevel > 0 &&				// Using -m errLevel option
					_errSeverity >= _errorLevel) {		
					_out.println("Msg " + sybe.getErrorCode() + 
							", Level " + sybe.getSeverity() + 
							", State " + sybe.getState() + ":");	
					handled = true;
				} else if (_errSeverity <= 10 ) {			// RS (at least) sends SybSQLException
					_out.printLine(sqe.getMessage());		// with severity = 0
					handled = true;
				}
				else {
					_out.println("Msg " + sybe.getErrorCode() + 
							", Level " + sybe.getSeverity() + 
							", State " + sybe.getState() + ":");
					_out.println("Server '" + sybe.getServerName() + 
							"', Line " + sybe.getLineNumber() + ":");
					_out.printLine(sybe.getMessage());
					handled = true;
				}

			} else if (sqe instanceof SQLException) {
				String SQLState = sqe.getSQLState();
				if (SQLState.equalsIgnoreCase("JZ0TO")) { 
					// Command timeout message
					_out.println(sqe.getMessage());
					handled = true;
				} else if (SQLState.equalsIgnoreCase("JZ006")) {
					// Seem to be IOExceptions on stack
					// _out.println(sqe.getMessage());
					if (sqe.getMessage().contains("JZ0EM")) {
						// End of Data == connection closed.
						_curConn = null;
					}
				} else if (SQLState.equalsIgnoreCase("JZ0C0") ||
						   SQLState.equalsIgnoreCase("JZ0C1")) {
					// Connection closed (eg: server shutdown?)
					_curConn = null;
				} else {
					// Print the Exception
					_out.println("SQLException : " +
						"SqlState: " + sqe.getSQLState()  +
						" " + sqe.getMessage() +
						", ErrorCode: " + sqe.getErrorCode());
					handled = true;
				}
				notaSybE = sqe;
			} else {
				System.out.println("Unexpected exception : " +
						"SqlState: " + sqe.getSQLState()  +
						" " + sqe.toString() +
						", ErrorCode: " + sqe.getErrorCode());
				handled = true;
				notaSybE = sqe;
			}
			// If debug is enabled and exception not yet displayed:
			if (_debug > 0 && handled == false){
				System.out.println("DEBUG: Exception : " +
						sqe.toString());
			}
			sqe = sqe.getNextException();
		}
		return notaSybE;
	}
} // End of Misql class
