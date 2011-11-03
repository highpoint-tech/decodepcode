package decodepcode;

import java.util.Date;

public class SQLobject implements PeopleToolsObject 
{
	String recName, sql, lastChangedBy, source, market, dbType;
	Date lastChanged;
	
	public SQLobject( String _recName, String _sql, String _lastChangedBy, Date _lastChanged, 
					String _market, String _dbType)
	{
		recName = _recName; sql = _sql; lastChangedBy = _lastChangedBy; lastChanged = _lastChanged;
		market = _market; dbType = _dbType;
	}
	
	public String[] getKeys() 
	{
		String[] a ;
		if (!"GBL".equals(market))
		{
			if (!" ".equals(dbType))
			{
				a = new String[3];
				a[2] = dbType;
				
			}
			else
				a = new String[2];
			a[1] = market;
		}
		else
			if (!" ".equals(dbType))
			{
				a = new String[2];
				a[1] = dbType;
			}
			else
				a = new String[1];
		a[0] = recName;
		return a;
	}

	public int getPeopleCodeType() {
		return -1;
	}

	public String getLastChangedBy() {
		return lastChangedBy;
	}

	public Date getLastChangedDtTm() {
		return lastChanged;
	}

	public String getRecName() {
		return recName;
	}

	public String getSql() {
		return sql;
	}

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public int[] getKeyTypes() {
		// TODO Auto-generated method stub
		return null;
	}

}
