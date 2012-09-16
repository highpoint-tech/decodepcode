package decodepcode;

import java.io.File;
import java.io.IOException;

public class DirTreePTmapper implements PToolsObjectToFileMapper {
	File rootDir;
	public DirTreePTmapper( ) {}

	public DirTreePTmapper( File _rootDir)
	{
		rootDir = _rootDir;
	}
	public File getFile(PeopleToolsObject obj, String extension)
			throws IOException 
	{
		String pcType = JDBCPeopleCodeContainer.objectTypeStr(obj.getPeopleCodeType());
		File f = new File(rootDir, pcType);
		int last = -1;
		for (int i = 0; i < obj.getKeys().length; i++)
			if (obj.getKeys()[i] != null && obj.getKeys()[i].trim().length() > 0)
				last = i;
		for (int i = 0; i < last; i++)
		{			
			String dirName = obj.getKeys()[i].trim().replace("/", "_").replace("?", "_").replace("*", "_");
			f = new File(f, dirName);
		}	
		f.mkdirs();
		f = new File(f, obj.getKeys()[last].trim().replace("/", "_").replace("?", "_").replace("*", "_") + "." + extension);
		return f;
	}
	
	public File getFileForSQL(SQLobject sqlObject, String extension) throws IOException {
		String f1;
		if (sqlObject.sqlType == 2)
			f1 = "SQL";
		else
			if (sqlObject.sqlType == 0)
				f1 = "SQL_0";
			else
				if (sqlObject.sqlType == 1)
					f1 = "SQL_AE";
				else
					if (sqlObject.sqlType == 6)
						f1 = "XSLT";
					else
						f1 = "SQL" + sqlObject.sqlType;
		File f = new File(rootDir, f1);
		for ( int i = 0; i < sqlObject.getKeys().length; i++) // optional MARKET and/or DBTYPE
			f = new File(f, sqlObject.getKeys()[i]); 
		f.mkdirs();
		return new File(f, sqlObject.getKeys()[0]+ "." + extension);
	}
	public String getPath(PeopleToolsObject obj, String extension) {
		String pcType = JDBCPeopleCodeContainer.objectTypeStr(obj.getPeopleCodeType());
		String f = "/" + pcType + "/";
		int last = -1;
		for (int i = 0; i < obj.getKeys().length; i++)
			if (obj.getKeys()[i] != null && obj.getKeys()[i].trim().length() > 0)
				last = i;
		for (int i = 0; i <= last; i++)
			f += obj.getKeys()[i].trim() + "/";
		f += obj.getKeys()[last].trim() + "." + extension;
		return f;
	}
	public String getPathForSQL(SQLobject sqlObject, String extension) {
		
		
			String f;
			if (sqlObject.sqlType == 2)
				f = "/SQL";
			else
				if (sqlObject.sqlType == 0)
					f = "/SQL_0";
				else
					if (sqlObject.sqlType == 1)
						f = "/SQL_AE";
					else
						if (sqlObject.sqlType == 6)
							f = "/XSLT";
						else
							f = "/SQL" + sqlObject.sqlType;
			for ( int i = 0; i < sqlObject.getKeys().length; i++) // optional MARKET and/or DBTYPE
				f = f + "/" + sqlObject.getKeys()[i]; 
			return f + "/" + sqlObject.getKeys()[0]+ "." + extension;
		
	}

}
