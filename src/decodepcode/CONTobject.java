package decodepcode;

import java.io.IOException;
import java.util.Date;

public class CONTobject implements PeopleToolsObject {

	
	String contName, contFmt, lastChangedBy;
	int altContNum, contType;
	Date lastChanged;
	String contData;
	byte[] contDataBytes;
	
	public CONTobject(String contName, String contFmt, String lastChangedBy, int altContNum, int contType,
			Date lastChanged, byte[] contDataBytes) throws IOException {
		super();
		this.contName = contName;
		this.contFmt = contFmt;
		this.lastChangedBy = lastChangedBy;
		this.altContNum = altContNum;
		this.contType = contType;
		this.lastChanged = lastChanged;
		if (contType == 4){
			this.contData = new String(contDataBytes, "UnicodeLittleUnmarked");
		} else {
			this.contDataBytes = contDataBytes;
		}
	}
	
	
	
	public byte[] getContDataBytes() {
		if (contType == 4){
			return contData.getBytes();
		} else {
			return contDataBytes;
		}
	}
	
	public String[] getKeys() {
		// TODO Auto-generated method stub
		return null;
	}

	public int[] getKeyTypes() {
		// TODO Auto-generated method stub
		return null;
	}

	public int getPeopleCodeType() {
		return -1;
	}

	public Date getLastChangedDtTm() {
		return lastChanged;
	}

	public String getLastChangedBy() {
		return lastChangedBy;
	}

	public String getSource() {
		// TODO Auto-generated method stub
		return null;
	}

}
