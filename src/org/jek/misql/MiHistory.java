package org.jek.misql;

import java.util.ArrayList;

/**
 * @author jek
 *
 */
public class MiHistory {

	static final int AVPERBATCH = 2048;			// Max size will be AVPERBATCH * size;
	private ArrayList<StringBuffer> batchList;	// The batch ArrayList
	private int maxsize;						// Max no of batches
	private int totalChars;
	private int	lastBatch = 0;					// Number of the last batch (used for display)

	public int getLastBatch() {
		return lastBatch;
	}

	/**
	 * Default to history of 20 batches
	 */
	public MiHistory() {
		maxsize = 20;
		batchList = new ArrayList<StringBuffer>(maxsize);		
	}
	
	/** Create a history object to contain 'size' batches
	 * @param size of batch history
	 */
	public MiHistory(int size) {
		if (size < 1) {
			size = 1;
		}
		maxsize = size;
		batchList = new ArrayList<StringBuffer>(size);		
	}

	public int getMaxsize() {
		return maxsize;
	}

	public void setMaxsize(int size) {
		if (size > 0) {
			this.maxsize = size;
		}		
	}

	/** Add the batch to the history
	 * @param batchBuff
	 */
	public void add(StringBuffer batchBuff) {
		// Skip adding :history command to history list.
		if (batchBuff.toString().equalsIgnoreCase(":history")
				|| batchBuff.toString().equalsIgnoreCase(":h"))
			return;
		
		lastBatch++;	// Inc last batch number
			
		// If the history is full remove the oldest batch
		if (batchList.size() >= maxsize) {
			totalChars -= batchList.get(0).length();
			batchList.remove(0);			
		}
		
		// If this batch is too big to add - just add empty batch
		if (batchBuff.length() > maxsize * AVPERBATCH) {
			batchList.add(new StringBuffer("\n"));
			return;
		}
		
		// Remove enough old batches to allow space for the new one
		while (totalChars + batchBuff.length() > maxsize * AVPERBATCH) {
			totalChars -= batchList.get(0).length();
			batchList.remove(0);			
		}
		
		// Now add the batch to the list
		batchList.add(batchBuff);
		totalChars += batchBuff.length();
		return;
	}

	/** This method processes history command lines:<p>
	 * '!!' return the last batch <br>
	 * '!n' return the 'n'th batch <br>
	 * '!-n' return the relative '-n'th batch <br>
	 * @param cmd The command line
	 * @param _out Used to output the 
	 */
	public StringBuffer command(String cmd) {
		// '!!' get last batch in list
		int bNum = 0;
		try {
			if (cmd.equalsIgnoreCase("!!")) {
				return(batchList.get(batchList.size()-1));
			}
			try {
				bNum = Integer.parseInt(cmd.substring(1));
				if (bNum < 0) {
					bNum = (batchList.size()) + bNum; // Relative from end
				} else {
					bNum = (batchList.size()-1) - (lastBatch - bNum);
				}
				return(batchList.get(bNum));
			} catch (NumberFormatException nfe) {
				// Ignore non-numerical arg.
				return null;
			}
		} catch (IndexOutOfBoundsException e) {
			return null;
		}
	}

	/** Print the history to the output stream
	 * @param _out
	 */
	public void print(MiPrinter _out) {
		// Calc first batch number
		int bNum = lastBatch - batchList.size() + 1;
		for (StringBuffer hBatch : batchList) {			
			boolean firstLine = true;
			for (String bLine : hBatch.toString().split("\n")) {
				if (firstLine) {
					_out.printf("%4d : %s\n", bNum, bLine);
					firstLine = false;
				} else {
					_out.printf("%4s   %s\n", "", bLine);
				}
			}			
			bNum++;
		}
	}

}
