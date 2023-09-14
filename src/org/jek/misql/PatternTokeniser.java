package org.jek.misql;

/** PatternTokeniser -
	simple class to break a string into tokens.
	Very like StringTokeniser except that it uses token delimiters as strings
	not just a set of characters 'OR'd.

@author jek <p>
History:
22 Aug 2008 - jek:   Copied from old Casetools Eclipse<br>
*/

public class PatternTokeniser {
   private String patString = null;		// Store of full String
	private String delim = " ";			// Store of token delimitter string
	private int curIdx = 0;				// Store of current position in patString

   // constructors
   public PatternTokeniser() {
   }

	public PatternTokeniser(String fStr) {
		this.patString = fStr;
   }

   public PatternTokeniser(String fStr, String tStr) {
		this.patString = fStr;
		this.delim = tStr;
   }

   // are there anymore tokens to consider?
   public boolean hasMoreTokens() {
		return (curIdx < patString.length());
   }

   // are there anymore tokens to consider delimited by delim?
   public boolean hasMoreTokens(String delim) {
		return (patString.indexOf(delim, curIdx) > -1);
   }

   // returns the next token upto tStr
   public String nextToken(String tStr) {
		delim = tStr;
		return (nextToken());
   }

	// returns the next token up to delim
	public String nextToken() {
		String retString;

		int endTok = patString.indexOf(delim, curIdx);

		if (endTok == -1)	// No token found
		{
			retString = patString.substring(curIdx);	// Return rest of string.
			curIdx = patString.length();	// Point to end of input.
		}
		else
		{
			retString = patString.substring(curIdx, endTok);	// Return upto delim
			curIdx = endTok + delim.length();					// Inc. ptr past delim
		}
		// System.out.println("nextToken: ["+patString+"] "+curIdx+", ["+retString+"]");
		return(retString);
   }
}
