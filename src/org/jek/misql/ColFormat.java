package org.jek.misql;

class ColFormat		// Class to hold format and colname string pairs.
{
	public int colNum;				// Display column number
	public int rsColNum;			// ResultSet column number
	public String colName = null;
	public int dispSize;
	public int colType;

	// Constructor
	public ColFormat(int colNum, int rsColNum, String colName, int dispSize, int colType)
	{
		this.colNum = colNum;
		this.rsColNum = rsColNum;
		this.colName = colName;
		this.dispSize = dispSize;
		this.colType = colType;
	}
}