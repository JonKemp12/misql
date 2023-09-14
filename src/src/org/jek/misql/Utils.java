package org.jek.misql;


import java.util.Properties;

/** Utils contains a number of utility methods
 * just to keep the code a bit tidier.
 * Not very Java - perhaps!
 * History:
 * 27 Sep 2008 - jek: Created
 * @author jek
 *
 */
public class Utils {
	
	/** args2Props<p>
	 * Takes an array of strings (typically String[] args) and processes
	 * them into a Properties list.
	 * Args are of the form '-o [value]' or '--option[=value]'.
	 * Any non-option args are returned as the value of the "args" Property.
	 * @param args
	 * @return
	 */
	public static Properties args2Props(String[] args) {
		Properties props = new Properties();
		props.put("args", "");		// Initialise an empty 'args' value
		
		for (int i = 0; i < args.length; i++) {
			String arg = args[i];
			if (arg.regionMatches(0, "--", 0, 2)) {	
				// This is an  --option[=value] type option
				arg = arg.substring(2); // Lose the '--'
				// Look for '=' for 'option=value' pair
				String[] optValue = arg.split("=");
				if (optValue.length == 1)
					props.setProperty(optValue[0], "");
				if (optValue.length == 2)
					props.setProperty(optValue[0], optValue[1]);

				continue;
			}
			
			if (arg.regionMatches(0, "-", 0, 1)) {	
				// This is an -o[value] or -o [value] type option
				arg = arg.substring(1);				// Lose the '-'
				
				if(arg.length() > 1)
				{
					// The argument value follows (i.e.  -Uusername)
					props.setProperty(arg.substring(0, 1), arg.substring(1));
				}
				else
				{
					if( i+1 == args.length || args[i+1].regionMatches(0, "-", 0, 1) )
					{
						// We are either at the last argument or the next option
						// starts with '-'.
						props.setProperty(arg, "");
					}
					else
					{
						// The argument value is the next argument (i.e.  -U username)
						props.setProperty(arg, args[i+1]);
						i++;	// Increment to the next arg
					}
				}
			continue;	
			}
			// This is an argument - not an option
			props.put("args", props.get("args ") + arg);			
		}
		return props;
	}

	// 
	/**Replicates inStr to return outStr to be width wide.
	 * @param inStr String to be replicated
	 * @param width to this width
	 * @return replicated string
	 */
	public static String replicate(String inStr, int width) {
		String outStr = "";
		for (int i = 0; i < width/inStr.length(); i++) {
			outStr = outStr + inStr;
		}
		return outStr;
	}
	
	/** Copies inStr to outStr replacing newline (\n), tab (\t) and '%'sequences with printf characters. 
	 * @param inStr input string
	 * @return output string
	 */
	public static String escape(String inStr) {
		StringBuffer outStr = new StringBuffer();

		for (int i = 0; i < inStr.length(); i++) {
			char c = inStr.charAt(i);
			switch (c) {
			case '%':		// Need to escape '%' to double '%%'
				outStr.append("%%");
				break;

			case '\\':		// Found a slash
				switch (inStr.charAt(i+1)) {
				case 'n':	// Add newline
					outStr.append('\n');
					i++;
					break;
				case 't':	// Add tab char
					outStr.append('\t');
					i++;
					break;
				case '\\':	// Add single '\'
				outStr.append('\\');
						i++;
						break;
	
				default:	// Anything else - just add the '\'
					outStr.append('\\');
					break;
				}
				break;

			default:
				outStr.append(c);
				break;
			}
		}
		return outStr.toString();
	}

}
