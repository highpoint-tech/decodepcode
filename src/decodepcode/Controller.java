package decodepcode;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Writer;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

import decodepcode.JDBCPeopleCodeContainer.ContainerProcessor;
import decodepcode.JDBCPeopleCodeContainer.KeySet;
import decodepcode.JDBCPeopleCodeContainer.StoreInList;

/**
 * 
 * Contains the static methods to run the PeopleCode extraction / decoding
 *
 */

/*
 * Copyright (c) 2011 Erik H (erikh3@users.sourceforge.net)

Permission to use, copy, modify, and/or distribute this software for any
purpose with or without fee is hereby granted, provided that the above
copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
*/

public class Controller {
	public static Connection dbconn;
	static Statement st;
	static Logger logger = Logger.getLogger(Controller.class.getName());
	static String dbowner;
	static boolean writePPC = false;
	static boolean reverseEngineer= false; 
	static long countPPC=0, countSQL= 0;
	
	static Properties props;
	static
	{
		try
		{
			props= readProperties();
		} catch (IOException ex)
		{
			logger.severe("Unable to read properties : " + ex);
		}
	}
	
	public static List<JDBCPeopleCodeContainer> getPeopleCodeContainers(String whereClause) throws ClassNotFoundException, SQLException
	{
		List<JDBCPeopleCodeContainer> list = new ArrayList<JDBCPeopleCodeContainer>();
		StoreInList s = new StoreInList(list);
		try {
			makeAndProcessContainers( whereClause, s);
		} catch (IOException io) {}
		return list;
	}	
	
	public static void writeSQLforProjectToFile(String projectName, File baseDir) throws ClassNotFoundException, SQLException, IOException
	{
		writeSQLforProjectToFile( projectName, new DirTreePTmapper(baseDir));
	}
	public static void getJDBCconnection() throws ClassNotFoundException, SQLException
	{
		logger.info("Getting JDBC connection");
		Class.forName(props.getProperty("driverClass"));
		dbconn = DriverManager.getConnection(props.getProperty("url"), props.getProperty("user"), props.getProperty("password"));		
	}
	
	interface ParameterSetter
	{
		void setParameters( PreparedStatement ps) throws SQLException;
	}

	public static void makeAndProcessContainers( String whereClause, ContainerProcessor processor) throws ClassNotFoundException, SQLException, IOException
	{
		makeAndProcessContainers( whereClause, processor, null);
	}
	
	public static void makeAndProcessContainers( String whereClause, ContainerProcessor processor, ParameterSetter callback) throws ClassNotFoundException, SQLException, IOException
	{
		getJDBCconnection();
		String q = "select "+ KeySet.getList() + ", LASTUPDOPRID, LASTUPDDTTM from " + dbowner + "PSPCMPROG pc " + whereClause + " and pc.PROGSEQ=0";
		logger.info(q);
		PreparedStatement st0 =  dbconn.prepareStatement(q);
		if (callback != null)
			callback.setParameters(st0);
		ResultSet rs = st0.executeQuery();
		while (rs.next())
		{
			processor.process(new JDBCPeopleCodeContainer(dbconn, dbowner, rs));
			logger.info("Completed JDBCPeopleCodeContainer" );
			countPPC++;
		}
		dbconn.close();
	}
	/*
	 * select 
d.LASTUPDOPRID, d.LASTUPDDTTM, 
td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT 
from PSSQLDEFN d, PSSQLTEXTDEFN td where d.SQLID=td.SQLID 
---and td.SQLID in (select OBJECTVALUE1 from PSPROJECTITEM where PROJECTNAME='TEST2')
	 */
	static void writeSQLtoFile( ResultSet rs, PToolsObjectToFileMapper mapper) throws SQLException, IOException
	{
		while (rs.next())
		{
			String recName = rs.getString("SQLID");
			String sql = rs.getString("SQLTEXT");
			if (recName == null || sql == null)
				continue;
			File sqlFile = mapper.getFileForSQL(recName, "sql");
			logger.info("Creating " + sqlFile);
			FileWriter fw = new FileWriter(sqlFile);
//			dbedit.internal.parser.Formatter formatter = new Formatter();
//			sql = formatter.format(sql, 0, null, System.getProperty("line.separator"));
			fw.write(sql);
			fw.close();
			File infoFile = mapper.getFileForSQL(recName, "last_update");
			PrintWriter pw = new PrintWriter(infoFile);
			pw.println(rs.getString("LASTUPDOPRID"));
			pw.println(ProjectReader.df2.format(rs.getTimestamp("LASTUPDDTTM")));
			pw.close();
			countSQL++;
		}
	}

	public static void writeSQLforProjectToFile(String projectName, PToolsObjectToFileMapper mapper) throws ClassNotFoundException, SQLException, IOException
	{
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td, " 
				+ dbowner + "PSPROJECTITEM pi  where d.SQLID=td.SQLID and d.SQLID=pi.OBJECTVALUE1 and pi.OBJECTID1=65 and pi.OBJECTVALUE2=td.SQLTYPE and DBTYPE = ' ' and pi.PROJECTNAME='" + projectName + "'";  
		getJDBCconnection();
		Statement st0 =  dbconn.createStatement();
		logger.info(q);
		ResultSet rs = st0.executeQuery(q);		
		writeSQLtoFile(rs, mapper);
		st0.close();
	}
	
	public static void writeSQLsinceDateToFile(java.sql.Timestamp date, File baseDir) throws ClassNotFoundException, SQLException, IOException
	{
		writeSQLsinceDateToFile( date, new DirTreePTmapper(baseDir));
	}
	
	public static void writeSQLsinceDateToFile( java.sql.Timestamp date, PToolsObjectToFileMapper mapper) throws ClassNotFoundException, SQLException, IOException
	{
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td " 
				+ " where td.SQLTYPE=2 and d.SQLID=td.SQLID and d.LASTUPDDTTM >= ?";  
		getJDBCconnection();
		PreparedStatement st0 =  dbconn.prepareStatement(q);
		st0.setTimestamp(1, date);
		logger.info(q);
		ResultSet rs = st0.executeQuery();		
		writeSQLtoFile(rs, mapper);
		st0.close();
	}

	public static void writeCustomSQLtoFile(File baseDir) throws ClassNotFoundException, SQLException, IOException
	{
		writeCustomSQLtoFile( new DirTreePTmapper(baseDir));
	}
	
	public static void writeCustomSQLtoFile(PToolsObjectToFileMapper mapper) throws ClassNotFoundException, SQLException, IOException
	{
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td " 
				+ " where td.SQLTYPE=2 and d.SQLID=td.SQLID and d.LASTUPDOPRID <> 'PPLSOFT'";  
		getJDBCconnection();
		PreparedStatement st0 =  dbconn.prepareStatement(q);
		logger.info(q);
		ResultSet rs = st0.executeQuery();		
		writeSQLtoFile(rs, mapper);
		st0.close();
	}
		
	public static List<JDBCPeopleCodeContainer> getPeopleCodeContainersForProject(Properties props, String projectName) throws ClassNotFoundException, SQLException
	{
		String where = " , " + dbowner + "PSPROJECTITEM pi where  (pi.OBJECTVALUE1= pc.OBJECTVALUE1 and pi.OBJECTID1= pc.OBJECTID1) "
		    + " and ((pi.OBJECTVALUE2= pc.OBJECTVALUE2 and pi.OBJECTID2= pc.OBJECTID2 and pi.OBJECTVALUE3= pc.OBJECTVALUE3 and pi.OBJECTID3= pc.OBJECTID3 and pi.OBJECTVALUE4= pc.OBJECTVALUE4 and pi.OBJECTID4= pc.OBJECTID4)"
			+ "  or (pi.OBJECTTYPE  = 43 and pi.OBJECTVALUE3 = pc.OBJECTVALUE6))  "
			+ " and pi.PROJECTNAME='" + projectName + "' and pi.OBJECTTYPE in (8 , 9 ,39 ,40 ,42 ,43 ,44 ,46 ,47 ,48 ,58)";
		return getPeopleCodeContainers( where);
	}
	
	public static Properties readProperties() throws IOException
	{
		Properties props= new Properties();
		props.load(new FileInputStream("DecodePC.properties"));
		dbowner = props.getProperty("dbowner");
		dbowner = dbowner == null? "" : dbowner + ".";
		return props;
	}
	
	public static List<JDBCPeopleCodeContainer> getPeopleCodeContainersForApplicationPackage(String packageName) throws ClassNotFoundException, SQLException
	{
		String where = "  , " + dbowner + "PSPACKAGEDEFN pk  where pk.PACKAGEROOT  = '" + packageName + "' and pk.PACKAGEID    = pc.OBJECTVALUE1    and pc.OBJECTVALUE1 = pk.PACKAGEROOT ";
		return getPeopleCodeContainers( where);
	}
	
	public static List<JDBCPeopleCodeContainer> getAllPeopleCodeContainers() throws ClassNotFoundException, SQLException
	{
		String where = " where (1=1) ";
		return getPeopleCodeContainers( where);
	}
	
	public static void writeToDirectoryTree( List<JDBCPeopleCodeContainer> containers, File rootDir) throws IOException, SQLException, ClassNotFoundException
	{	
		logger.info("Writing to directory tree " + rootDir);
		rootDir.mkdirs();
		DirTreePTmapper mapper= new DirTreePTmapper(rootDir);
		for (JDBCPeopleCodeContainer p: containers)
		{
			p.writeBytesToFile(mapper.getFile(p, "bin"));
			p.writeReferencesToFile(mapper.getFile(p, "references"));
		}
		logger.info("Finished writing to directory tree");
	}
		
	public static void writeProjectToDirectoryTree2( String project, File rootDir) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Retrieving bytecode for project " + project);
		List<JDBCPeopleCodeContainer> containers;
		containers = getPeopleCodeContainersForProject(props, project);
		writeToDirectoryTree(containers, rootDir);
	}
	
	public static void writeProjectToDirectoryTree( String projectName, File rootDir) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Starting to write PeopleCode for project " + projectName + " to directory tree " + rootDir);
		String whereClause = " , " + dbowner + "PSPROJECTITEM pi where  (pi.OBJECTVALUE1= pc.OBJECTVALUE1 and pi.OBJECTID1= pc.OBJECTID1) "
	    + " and ((pi.OBJECTVALUE2= pc.OBJECTVALUE2 and pi.OBJECTID2= pc.OBJECTID2 and pi.OBJECTVALUE3= pc.OBJECTVALUE3 and pi.OBJECTID3= pc.OBJECTID3 and pi.OBJECTVALUE4= pc.OBJECTVALUE4 and pi.OBJECTID4= pc.OBJECTID4)"
		+ "  or (pi.OBJECTTYPE  = 43 and pi.OBJECTVALUE3 = pc.OBJECTVALUE6))  "
		+ " and pi.PROJECTNAME='" + projectName + "' and pi.OBJECTTYPE in (8 , 9 ,39 ,40 ,42 ,43 ,44 ,46 ,47 ,48 ,58)";
		makeAndProcessContainers( whereClause, new WriteDecodedPPCtoDirectoryTree(rootDir));
		logger.info("Finished writing .pcode files");		
		writeSQLforProjectToFile(projectName, rootDir); 		
	}

	
	static class WriteToDirectoryTree implements ContainerProcessor
	{
		PToolsObjectToFileMapper mapper;
		WriteToDirectoryTree( PToolsObjectToFileMapper _mapper)
		{
			mapper = _mapper;
		}
		WriteToDirectoryTree( File rootDir)
		{
			this( new DirTreePTmapper(rootDir));
		}
		public void process(JDBCPeopleCodeContainer p) throws IOException 
		{
			p.writeBytesToFile(mapper.getFile(p, "bin"));
			p.writeReferencesToFile(mapper.getFile(p, "references"));
		}		
	}
	
	static void writeToDirectoryTree( String whereClause, File rootDir) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write bin/ref files to directory tree " + rootDir);
		makeAndProcessContainers( whereClause, new WriteToDirectoryTree(rootDir));
		logger.info("Finished writing bin/ref files");
	}

	public static void writeAllPPCtoDirectoryTree( File rootDir) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Retrieving all PeopleCode bytecode in database" );
		writeToDirectoryTree(" where 1=1 ", rootDir);
	}

	static class WriteDecodedPPCtoDirectoryTree implements ContainerProcessor
	{
		PToolsObjectToFileMapper mapper;
		String extension;
		PeopleCodeParser parser = new PeopleCodeParser();
		WriteDecodedPPCtoDirectoryTree( PToolsObjectToFileMapper _mapper, String _extension)
		{
			mapper = _mapper;
			extension = _extension;
		}
		WriteDecodedPPCtoDirectoryTree( File rootDir, String _extension)
		{
			this( new DirTreePTmapper(rootDir), _extension);
		}
		WriteDecodedPPCtoDirectoryTree( File rootDir)
		{
			this(rootDir, "pcode");
		}
		public void process(JDBCPeopleCodeContainer p) throws IOException 
		{
			FileWriter w = new FileWriter(mapper.getFile(p, extension));
			try {
				parser.parse(p, w);
				File infoFile = mapper.getFile(p, "last_update");
				PrintWriter pw = new PrintWriter(infoFile);
				pw.println(p.getLastChangedBy());
				pw.println(ProjectReader.df2.format(p.getLastChangedDtTm()));
				pw.close();			
				
			} 
			catch (IOException e) { throw e; }
			catch (Exception e)
			{
				logger.severe("Error parsing PeopleCode for " + p.getCompositeKey() + ": " + e);
				FileWriter w1 = new FileWriter(mapper.getFile(p, "log"));
				PrintWriter w2 = new PrintWriter(w1); 
				w1.write("Error decoding PeopleCode: "+ e);
				e.printStackTrace(w2);
				w1.close();
			}
			w.close();			
		}		
	}
		
	public static void writeDecodedPPCtoDirectoryTree( String whereClause, File rootDir) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write decoded PeopleCode files to directory tree " + rootDir);
		makeAndProcessContainers( whereClause, new WriteDecodedPPCtoDirectoryTree(rootDir));
		logger.info("Finished writing .pcode files");		
	}
	
	static class DateSetter implements ParameterSetter
	{
		java.sql.Timestamp d;
		DateSetter( java.sql.Timestamp _d) { d = _d; }
		public void setParameters(PreparedStatement ps) throws SQLException {
			ps.setTimestamp(1, d);			
		}
	}	
	public static void writeDecodedRecentPPCtoDirectoryTree( java.sql.Timestamp fromDate, File rootDir) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write decoded PeopleCode with time stamp after " + fromDate + "  to directory tree " + rootDir);
		String whereClause = " where LASTUPDDTTM > ?";
		makeAndProcessContainers( whereClause, new WriteDecodedPPCtoDirectoryTree(rootDir), new DateSetter(fromDate));
		logger.info("Finished writing .pcode files");		
	}

	public static void writeCustomizedPPCtoDirectoryTree( File rootDir) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write customized PeopleCode files to directory tree " + rootDir);
		String whereClause = " where pc.LASTUPDOPRID <> 'PPLSOFT'";
		makeAndProcessContainers( whereClause, new WriteDecodedPPCtoDirectoryTree(rootDir));
		logger.info("Finished writing .pcode files");		
	}	
	
	static void writeStats()
	{
		System.out.println("Ready; processed "+ countPPC + " PeopleCode segment(s), and " + countSQL + " SQL definition(s)");		
	}
	/**
	 * Run from command line:
	 *  Arguments: project name, or 'since' + date (in yyyy/MM/dd format), or 'since-days' + #days, or 'custom'"
	 * @param a
	 */
	public static void main(String[] a)
	{
		try {
			File dir =	new File(".", "output");
			
			if (a.length > 1 && "since".equalsIgnoreCase(a[0]))
			{
				SimpleDateFormat sd = new SimpleDateFormat("yyyy/MM/dd");
				java.util.Date time = sd.parse(a[1]);
				java.sql.Timestamp d = new java.sql.Timestamp(time.getTime());
				writeDecodedRecentPPCtoDirectoryTree(d, dir);
				writeSQLsinceDateToFile( d, dir);
				writeStats();
				return;
			}

			if (a.length > 1 && "since-days".equalsIgnoreCase(a[0]))
			{
				java.util.Date time = new Date();
				long days = Long.parseLong(a[1]);
				java.sql.Timestamp d = new java.sql.Timestamp(time.getTime() - 24 * 60 * 60 * 1000 * days);
				writeDecodedRecentPPCtoDirectoryTree(d, dir);
				writeSQLsinceDateToFile( d, dir);
				writeStats();
				return;
			}
			
			if (a.length >= 1 && "custom".equalsIgnoreCase(a[0]))
			{
				writeCustomizedPPCtoDirectoryTree(dir);
				writeCustomSQLtoFile( dir);
				writeStats();
				return;				
			}

			if (a.length == 1)
			{
				writeProjectToDirectoryTree(a[0], dir);
				writeStats();
				return;
			}
			
			System.err.println("Arguments: project name, or 'since' + date (in yyyy/MM/dd format), or 'since-days' + #days, or 'custom'");
			//writeAllPPPCtoDirectoryTree(dir);
			//writeAllPPCtoDirectoryTree(new File("c:\\projects\\sandbox\\peoplecode\\HRDEV"));

		} catch (Exception e) {
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	
	public static void testWithJDBC() throws Exception
	{
		String whereClause = 
//			" pc ,dbo.PSPACKAGEDEFN pk  where pk.PACKAGEROOT  = 'BO_SEARCH'    and pk.PACKAGEID    = pc.OBJECTVALUE1    and pc.OBJECTVALUE1 = pk.PACKAGEROOT ";
			//" where OBJECTVALUE1 = 'BO_SEARCH                     ' and  OBJECTVALUE2 = 'Runtime                       ' and  OBJECTVALUE3 = 'Apps_Interaction              ' and  OBJECTVALUE4 = 'BusinessContact_Contact       ' and  OBJECTVALUE5 = 'OnExecute                     ' and  OBJECTVALUE6 = '                              ' and  OBJECTVALUE7 = '                              '";
		//	" where  OBJECTVALUE1 = 'BO_SEARCH                     ' and  OBJECTVALUE2 = 'Abstract                      ' and  OBJECTVALUE3 = 'BOSearchSQLGenerator          ' and  OBJECTVALUE4 = 'OnExecute                     ' and  OBJECTVALUE5 = '                              ' and  OBJECTVALUE6 = '                              ' and  OBJECTVALUE7 = '                              '";
		    " , PSPROJECTITEM pi where  pi.OBJECTVALUE1= pc.OBJECTVALUE1 and pi.OBJECTVALUE2= pc.OBJECTVALUE2 and pi.OBJECTVALUE3= pc.OBJECTVALUE3 and pi.OBJECTVALUE4= pc.OBJECTVALUE4 and pi.PROJECTNAME='CSR039'";
		List<JDBCPeopleCodeContainer> containers = getPeopleCodeContainers( whereClause);
		logger.info("Ready; "+ containers.size() + " container(s) created");
		
		for (int i = 0; i < containers.size(); i++)
			logger.info("# " + i + ": " + containers.get(i).keys.getWhere());
		
		
		if (containers.size() > 0)
		{
			logger.info("Start parsing");
			JDBCPeopleCodeContainer p = containers.get(0);
			logger.info(p.keys.getWhere());
			Writer w = new FileWriter("C:\\temp\\ppc.txt");
			PeopleCodeParser parser = new PeopleCodeParser();
			try {
				if (writePPC)
				{
					FileOutputStream fos = new FileOutputStream("c:\\temp\\test.ppc");
					fos.write(p.bytes);
					fos.close();
				}
				if (!reverseEngineer)
					parser.parse(p, w);
				else
				{
					p.readPeopleCodeTextFromFile(new File("c:\\temp\\peoplecode.txt"));
					parser.reverseEngineer(p);
				}
			} finally
			{
				w.close();
			}
		}	
	}

}
