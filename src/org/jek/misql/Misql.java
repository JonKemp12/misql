
package org.jek.misql;

import java.io.*;
import java.sql.*;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Properties;
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
 */
/**
 * @author jek
 *
 */
public class Misql {
	/**
	 * Holds default connection properties<br>
	 * See jConnect connection docs.
	 */
	static	exProperties _connProps = null;
	
	static	SybDriver sybDriver = null;
	public static	PrintStream _out	= null;
	
	static ArrayList<jConnection> _jConns;

	private static jConnection _curConn;

	private static int 		_verticalRows = 0;
	private static boolean 	_printHeader = true;
	private static boolean 	_setEcho = false;
	private static String 	_setPrompt = "@{lineNo}> ";
	private static String	_promptStr = "> ";
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
	private static String 	_serverName = "";
	private static int 		_cmdTimeout = 0;
	private static int 		_colWidth = 9999;

	private static boolean _prompt = false;	// Control if prompt is written.

	private static String _promptFmt;

	private static boolean _printFooter;

	private static String _rowTerm;

	/**
	 * @param args
	 * @return via System.exit with 0 for fail or 1 for success.
	 */
	public static void main(String[] args) {
		_connProps = new exProperties();
		_promptProps = new Properties();
		BufferedReader inFile = null;	// Starting default input reader (Usually System.in).		
		_out = System.out;				// Default output to System.out.
		
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

		
		// Process command line args
		if (!processCommandline(args))
        {
            System.exit(1);
        }
		
		// Setup current output file
		if (_outputFile.length() > 0) {
			try {
				// TODO : I doubt that this is correct!
				if (_setCharset.length() > 0) {
					_out = new PrintStream(_outputFile,_setCharset);
				} else
					_out = new PrintStream(_outputFile);
			} catch (FileNotFoundException e) {
				System.err.println("MISQL: Failed to open file " + _outputFile + " for writing.");
				System.exit(1);
			} catch (UnsupportedEncodingException e) {
				System.err.println("MISQL: display_charset " + _setCharset + " is not supported.");
			}
		}
		
         _out.println("_connProps is:" + _connProps);

		// get the input command stream
        if (_inputFile.length() > 0)
        {
            try
            {
                inFile = new BufferedReader(new FileReader(_inputFile));
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
            inFile = new BufferedReader(new InputStreamReader(System.in));
            _prompt  = true;		//
        }
        
        // Initialise the list of known connections.
        _jConns = new ArrayList<jConnection>(); 

		// If -U<user> is given then we need to create a default
		// connection. This needs to be as ISQL defaults!!
		if (_connProps.containsKey("USER")) {
			SybConnection _conn = null;
			String url = "";

			// How to use interfaces file in jConnect:
			// String url = "jdbc:sybase:jndi:file://D:/syb1252/ini/mysql.ini?myaseISO1”
			if (_serverName.indexOf(':') != -1) { // Assume this is a JDBC url as hostname:port
				url="jdbc:sybase:Tds:" + _serverName;
			} else {
				url = "jdbc:sybase:jndi:file://" + _interfacesFile + "?" + _serverName;
			}
			_colWidth = 80;		// ISQL default page width
			
			if (! _connProps.containsKey("PASSWORD")) {
				String password = promptForPassword(inFile);
				_connProps.setProperty("PASSWORD", password);
			}

			try {
				DriverManager.setLoginTimeout(_loginTimeout);
				_conn = (SybConnection)DriverManager.getConnection(url, _connProps);
				_curConn = new jConnection(_conn, "default");
				_curConn.userName = _connProps.getProperty("USER");
				_curConn.server = _serverName;
				messHandler(_conn.getWarnings());
				_jConns.add(_curConn);
				setPrompt(_setPrompt);
			}
			catch(SQLException ex) {
				messHandler(ex);
			}
			System.out.println("URL is: " + url + ", " + _connProps);
			if (_curConn == null) {		// Failure to connect is fatal here.
				System.exit(1);
			}
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
		
		while (true) {		// This loop to read a file whole file of batches.
			int	lineNo = 1;
			String line;
			int repeatBatch = 1;

			StringBuffer batchBuff = new StringBuffer();
			while (true) {	// This loop to read a batch
				// setPrompt("@{dbName}.@{lineNo}> ");
				printPrompt(lineNo);
				
				try {
					line = inFile.readLine();
				} catch (Exception ex){
					onError("Error reading input file " + inFile.toString());
					// Here if decided to retry
					continue;
				}
				
				if (line == null) {
					// End of file so just return
					return 0;
				}
				lineNo++;
				
				if (line.length() == 0)	// Empty line
				{
					if(_cmdEnd.length() == 0)	// This is special batch end
						break;
					else
						continue;
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
					if ( word[0].equals("quit") || 
						 word[0].equals("exit") || 
						 word[0].equals("QUIT") || 
						 word[0].equals("EXIT")
						) {
						// Exit immediately
						closeConns();
						System.exit(0);
					}

					// Check for 'reset *': lower case, no leading white space, ignore trailing
					if (word[0].equals("reset")) {
						batchBuff = new StringBuffer();
						lineNo = 1;
						continue;
					}

					// Is this a 'go' [<n-ttimes>] (aka cmdend) line?
					// Case is ignored
					if ( (word[0].toLowerCase().equals(_cmdEnd.toLowerCase())) ||
						 (word[0].equals(":go"))					// MISQL ':go [<repeatBatch>];
						) {
						if (word.length > 1 && word[1].matches("\\d+")) {
							try {
								repeatBatch = Integer.parseInt(word[2]);
							} catch (NumberFormatException nfe) {
								// Ignore non-numerical arg.
								repeatBatch = 1;
							}
						}
						break;				
					}
					// Support ISQL :r filename into current batch.
					if (word[0].equals(":r") && word.length > 1) {
						// Read contents of ':r <filename>' into batch buffer.
						batchBuff.append(readFile(word[1]));
					}
					
					// Test for MISQL command - first is ':'
					if (line.charAt(0) == ':') {
						// MISQL commands do not alter current batch.
						lineNo = doCommand(line, lineNo, inFile);
						continue;
					}
				}			
				
				// Add line to batch and get the next one:
				batchBuff.append(line);
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
	            SybStatement stmt = (SybStatement) _curConn.conn.createStatement();
	            // stmt.setEscapeProcessing(_escapeProcessing);
	            // _curConn.conn.clearWarnings();
	            
	            int batchLoop = repeatBatch;	// Use this to process results
	            
	            while (batchLoop-- > 0) {
		            int rowsAffected = 0;
		            boolean results = stmt.execute(batchBuff.toString());		            
					do {
						SybResultSet rs = null;
						if (results) {
							rs = (SybResultSet) stmt.getResultSet();
								// Print the results to outFile vertical or horizontal format
							if (_verticalRows == 0) {
								Hresults hrs = new Hresults(rs, _colWidth);	
								if (_printHeader) {
									hrs.printHeader(_out);
								}
								hrs.printRows(_out);
								// hrs.printFooter(_out);
							} else {
								printVresult(rs);
							}
							rowsAffected = stmt.getUpdateCount();
							printRowsAffected(rowsAffected);														
						} else {	// Just print rows affected.
							rowsAffected = stmt.getUpdateCount();
							printRowsAffected(rowsAffected);														
						}
						results = stmt.getMoreResults();
					} while (results || rowsAffected  != -1);					
				}	// RepeatBatch
	            if (repeatBatch > 1) {
					_out.println(repeatBatch + " xacts:");
				}
			} catch (Exception e) {
				System.err.println("Exception in readCommands: " + e);
				e.printStackTrace();
			}			
		} // End of input file
		
	}

	private static void printRowsAffected(int rowsAffected) {
        if (rowsAffected >= 0)
        {
            _out.println("("+rowsAffected + ") rows affected");
        }
	}



	private static void printVresult(ResultSet rs) {
		// TODO Auto-generated method stub
		
	}




	private static void closeConns() {
		// TODO Auto-generated method stub
		
	}



	private static Object readFile(String line) {
		// TODO Auto-generated method stub
		return null;
	}



	/** Performs all the processing for the MISQL special commands.<p>
	 * These commands start with ':', may span multiple lines and end with ';'.
	 * 
	 * @param line		The first line of the MISQL command (starts with ':')
	 * @param lineNo 	The starting batch line number. (Not used now and reset to leave batch numbers unchanged.)
	 * @param inFile	The input from from which to read
	 * @return			The last batch line number.	(To keep prompts correct).
	 */
	private static int doCommand(String line, int lineNo, BufferedReader inFile) {
		// This loop needs to read 'inFile' and build up an array of cmd words until
		// ';' is seen. Commands may contain strings in quotes which will be treated as a single word.
		// Any words after ';' (in a line) are ignored (but ';' in a string is ignored!
				
		StringBuffer sb = new StringBuffer(line);		// Keep a copy of the command line for error reporting
		ArrayList<String> cmdArray = new ArrayList<String>();
		
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
				for (String w : splitTerm[0].split("[ \\t\\x0B\\f\\r\\n]+")) {
					if (w.length() > 0) {
						cmdArray.add(w);
					}				
				}
				break;
			}
			
			// Here not found ';' in part[0] so split and add it to cmd
			for (String w : part[0].split("[ \\t\\x0B\\f\\r\\n]+")) {
				if (w.length() > 0) {
					cmdArray.add(w);
				}				
			}
			
			if (part.length == 1) {	//There was only 1 part and no term so
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
					return lineNo-1;
				}
				sb.append(line);
				continue;
			}
			
			if (part.length == 2) {		// part[1] contains first part of quoted string
				onError("Error: unmatched " + quote	);
				// ToDo: could read in multi-line string here but is that confusing?
				return lineNo-1;
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
		cmd = cmdArray.toArray(cmd);
		
		// Got a command so branch off to each command handler method
		boolean ok = false;
		while (cmd.length > 0) {
			if (cmd[0].equalsIgnoreCase(":")) {	// Just a comment
				ok = true;
				break;
			}
			if (cmd[0].equalsIgnoreCase(":open")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":on")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":close")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":pause")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":run")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":log")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":set")) {
				ok = doCmdSet(cmd);
				break;
			}
			if (cmd[0].equalsIgnoreCase(":onerror")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":print")) {
				break;
			}
			if (cmd[0].equalsIgnoreCase(":help")) {
				if (cmd.length ==2) {
					printHelp(cmd[1]);
				} else {
					printHelp("");
				}
				ok = true;
				break;
			}
			break;			
		}
		if(! ok) {
			// Here and don't recognise the command so print error:
			_out.println("Unrecognised MISQL command: '" + sb.toString() + "'");
		}
		return lineNo - 1;		
	}



	/** Process all ':set' commands
	 * @param cmd - array of command words
	 */
	private static boolean doCmdSet(String[] cmd) {
		// cmd[0] is ':set'
		// cmd[1-n] are the args
		if (cmd.length == 3) {
			// Set header output on or off
			if (cmd[1].equalsIgnoreCase("header")) {
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
			if (cmd[1].equalsIgnoreCase("footer")) {
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
			if (cmd[1].equalsIgnoreCase("colterm")) {
				if (cmd[2].equalsIgnoreCase("default"))
					_colTerm = "";
				else
					_colTerm = cmd[2];
				return true;
			}	
					
			// Set row terminator
			if (cmd[1].equalsIgnoreCase("rowterm")) {
				if (cmd[2].equalsIgnoreCase("default"))
					_rowTerm = "";
				else
					_rowTerm = cmd[2];
				return true;
			}	

			// Set row terminator
			if (cmd[1].equalsIgnoreCase("prompt")) {
				setPrompt(cmd[2]);
				return true;
			}	
		}
		
		// Here with invalid :set command
			onError(":set syntax error.");		
		return false;		
	}



	private static int onError(String string) {
		_out.println(string);
		return 0;
	}


	/** prints the prompt<p>
	 * Note that the printf args MUST be strings in the same order as the PROMPTVAR properties.<br>
	 * Any additional variables must be added to both PROMPTVAR and to the printf() args.
	 * @param lineNo
	 */
	private static void printPrompt(int lineNo) {
		String lineStr = String.valueOf(lineNo);
		if (lineNo == 0) {	// Special case :cmd prompt
			_out.printf(" : ");
			_out.flush();
			return;
		}
		_out.printf(_promptFmt, lineStr, _curConn.dbName, _curConn.connName, _curConn.userName, _curConn.server);
		_out.flush();		
	}
	private static final String PROMPTVAR[] = {
		"lineNo",
		"dbName",
		"connName",
		"userName",
		"server"
	};	
	
	/** parses command String to generate printf style prompt string.
	 * @param cmdStr - free txt including variables:<br>
	 * @{lineNo} @{dbName} @{connName} @{userName} @{server}
	 */
	private static void setPrompt(String cmdStr) {
		String fmtStr = "";
		cmdStr = cmdStr.replaceAll("%", "%%");	// Escape any % chars!
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
		_promptFmt = fmtStr;
	}

	/** promptForPassword:<p>
	 * Prompts for the password from the input file.
	 * @return the password as a string
	 */
	private static String promptForPassword(BufferedReader inFile) {
		if ( _prompt || _setEcho )	{	// If using stdin or _setEcho is on..
			_out.print("Password: ");		
		}
		try {
			return (inFile.readLine());
		}
		catch (Exception ex) {
			System.err.println("Failed to read password from input file"
					+ inFile.toString());
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
			iFile = sybaseEnv + "/interfaces?";
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
		
		// My 'special' in Tds.java
		_connProps.setProperty("FILTER_INFO_MSG", "False");
		
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
		
		System.out.println("processCommandLine: argProps is: " + argProps.toString());
		
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
					if ( optValue.length() > 0)
						_defaultDB = optValue;
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
					_colTerm= optValue;
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
						_colWidth = Integer.parseInt(optValue);
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
		String helpFileName = "doc/help-"+ topic +".txt";
		InputStream helpFile = Misql.class.getClassLoader().getResourceAsStream(helpFileName);
		if (helpFile == null) {
			if (topic.length() > 0) {
				_out.println("No help available on '"+topic+"'");
			}
			_out.println("Help is available on a number of topics. Try 'help help' for details.");
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
		while (sqe != null)
		{
			if (sqe instanceof SybSQLException) {	// Check this is a SybSQLException
				SybSQLException sybe = (SybSQLException) sqe;
				_out.println("Msg " + sybe.getErrorCode() + 
						", Level " + sybe.getSeverity() + 
						", State " + sybe.getState() + ":");
				_out.println("Server '" + sybe.getServerName() + 
						"', Line " + sybe.getLineNumber() + ":");
				_out.print(sybe.getMessage());

			} else if (sqe instanceof SybSQLWarning ||
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
					_out.print(sqe.getMessage());
					break;
				}
				
			} else if (sqe instanceof SQLException) {
				_out.println("SQLException : " +
						"SqlState: " + sqe.getSQLState()  +
						" " + sqe.toString() +
						", ErrorCode: " + sqe.getErrorCode());
				notaSybE = sqe;
			} else {
				System.out.println("Unexpected exception : " +
						"SqlState: " + sqe.getSQLState()  +
						" " + sqe.toString() +
						", ErrorCode: " + sqe.getErrorCode());
				notaSybE = sqe;
			}
			sqe = sqe.getNextException();
		}
		return notaSybE;
	}

} // End of Misql class
