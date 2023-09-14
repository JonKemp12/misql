/**
 * 
 */
package org.jek.misql;

import java.util.Properties;

/**
 * exProperties:<p>
 * Adds some useful methods to Properties class.
 * @author jek
 * History:
 * 22 Aug 2008 - jek:	Created (copied from CaseTools.
 *
 */
public class exProperties extends Properties {

	/**
	 * 
	 */
	private static final long serialVersionUID = 2122771070928016149L;

	/** fromHexString:
	 *	converts hexString to String and then reads from that key=value pairs.
	 * @param	hS		A string like "key=value" but hexadecimal encoded
	 */
	public void fromHexString(String hS) 
	{
		StringBuffer sb = new StringBuffer();

		for (int i=0; i<hS.length()/2; i++)
		{
			String ss = hS.substring(i*2, (i*2)+2);
			// System.out.print("."+ss);
			byte bb = Byte.parseByte(ss, 16);
			sb.append((char)bb);
		}

		fromString(sb.toString());
	}

	/** fromString:
	 *	 reads from String "key=value,..." pairs.
	 * @param	s	Input String
	 */
	public void fromString(String s) 
	{
		if (s.charAt(0) == '{' && s.charAt(s.length()-1) == '}')
		{
			s = s.substring(1,s.length()-1);
		}

		PatternTokeniser st = new PatternTokeniser(s);

		while (st.hasMoreTokens("="))
		{
			String pName = st.nextToken("=").trim();	// Get upto '=' in string.
			String pValue = null;

			pValue = st.nextToken(",").trim();		// ..get value.

			// System.out.println("["+pName+"] , ["+pValue+"]");
			this.put(pName, pValue);
		}
	}

	/** toHexString:
	 *	converts Properties to String and then to HexString.
	 */
	public String toHexString() 
	{
		String s = this.toString();		// Produces {key=value, key=value, key...}
		return(hexString(s));
	}

	/** hexString: 
	 *	converts input string to hex string
	 */
	private String hexString(String s)
	{
		byte b[] = s.getBytes();
		StringBuffer sb = new StringBuffer();

		for (int i=0; i<b.length; i++)
		{
			// System.out.print("["+Integer.toHexString((int)b[i]) + "]");
			sb.append(Integer.toHexString((int)b[i]));
		}
		return(sb.toString());
	}
	
	/**
	 *  Parse a command line argument.  Arguments may be supplied in
	 *  2 different ways:<p>
	 *  -Uusername<br>
	 *  -U username<br>
	 *  NOTE: empty args are treated as "" (empty string) and NOT as null value.<br>
	 *  @param    argv     Array of command line arguments
	 *  @param    pos      Current argument argv position
	 *  @param	  argKey   The key to set in _cmdLineArgs
	 *  @return            A value of 1 or 0 which is used to indicate args used. <0
	 */
	public int parseArguments(String argv[], int pos, String argKey)
	{
		int argc = argv.length-1; // # arguments specified
		String   arg = argv[pos].substring(1);
		int argLen  = arg.length(); // Length of arg
		int incrementValue = 0;

		if(argLen > 1)
		{
			// The argument value follows (i.e.  -Uusername)
			this.setProperty(argKey, arg.substring(1));
		}
		else
		{
			if( pos == argc || argv[pos+1].regionMatches(0, "-", 0, 1) )
			{
				// We are either at the last argument or the next option
				// starts with '-'.
				this.setProperty(argKey, "");
			}
			else
			{
				// The argument value is the next argument (i.e.  -U username)
				this.setProperty(argKey, argv[pos+1]);
				incrementValue= 1;
			}
		}
		return(incrementValue);
	}
	// END -- parseArguments()
}
