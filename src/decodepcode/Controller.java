package decodepcode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

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
	final static File lastTimeFile = new File("last-time.txt");

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
	
	public static List<PeopleCodeContainer> getPeopleCodeContainers(String whereClause) throws ClassNotFoundException, SQLException
	{
		List<PeopleCodeContainer> list = new ArrayList<PeopleCodeContainer>();
		StoreInList s = new StoreInList(list, null);
		try {
			makeAndProcessContainers( whereClause, s);
		} catch (IOException io) {}
		return list;
	}	
	
	public static void getJDBCconnection() throws ClassNotFoundException, SQLException
	{
		logger.info("Getting JDBC connection");
		Class.forName(props.getProperty("driverClass"));
		dbconn = DriverManager.getConnection(props.getProperty("url"), props.getProperty("user"), props.getProperty("password"));		
	}
	
	public interface ParameterSetter
	{
		public void setParameters( PreparedStatement ps) throws SQLException;
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
	static void processSQLs( ResultSet rs, ContainerProcessor processor) throws SQLException, IOException
	{
		while (rs.next())
		{
			String recName = rs.getString("SQLID");
			String sqlStr = rs.getString("SQLTEXT");
			if (recName == null || sqlStr == null)
				continue;
			Date date = new Date(rs.getTimestamp("LASTUPDDTTM").getTime());
			SQLobject sql = new SQLobject(recName.trim(), sqlStr.trim(), rs.getString("LASTUPDOPRID"), date);
			processor.processSQL(sql);
			countSQL++;
		}
	}

	public static void processSQLforProject(String projectName, ContainerProcessor processor) throws ClassNotFoundException, SQLException, IOException
	{
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td, " 
				+ dbowner + "PSPROJECTITEM pi  where d.SQLID=td.SQLID and d.SQLID=pi.OBJECTVALUE1 and pi.OBJECTID1=65 and pi.OBJECTVALUE2=td.SQLTYPE and DBTYPE = ' ' and pi.PROJECTNAME='" + projectName + "'";  
		getJDBCconnection();
		Statement st0 =  dbconn.createStatement();
		logger.info(q);
		ResultSet rs = st0.executeQuery(q);		
		processSQLs(rs, processor);
		st0.close();
	}
	
	
	public static void processSQLsinceDate( java.sql.Timestamp date, ContainerProcessor processor) throws ClassNotFoundException, SQLException, IOException
	{
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td " 
				+ " where td.SQLTYPE=2 and d.SQLID=td.SQLID and d.LASTUPDDTTM >= ?";  
		getJDBCconnection();
		PreparedStatement st0 =  dbconn.prepareStatement(q);
		st0.setTimestamp(1, date);
		logger.info(q);
		ResultSet rs = st0.executeQuery();		
		processSQLs(rs, processor);
		st0.close();
	}

	public static void writeCustomSQLtoFile(File baseDir) throws ClassNotFoundException, SQLException, IOException
	{
		processCustomSQLs( new WriteDecodedPPCtoDirectoryTree(baseDir));
	}
	
	public static void processCustomSQLs(ContainerProcessor processor) throws ClassNotFoundException, SQLException, IOException
	{
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td " 
				+ " where td.SQLTYPE=2 and d.SQLID=td.SQLID and d.LASTUPDOPRID <> 'PPLSOFT'";  
		getJDBCconnection();
		PreparedStatement st0 =  dbconn.prepareStatement(q);
		logger.info(q);
		ResultSet rs = st0.executeQuery();		
		processSQLs(rs, processor);
		st0.close();
	}
		
	public static List<PeopleCodeContainer> getPeopleCodeContainersForProject(Properties props, String projectName) throws ClassNotFoundException, SQLException
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
	
	public static List<PeopleCodeContainer> getPeopleCodeContainersForApplicationPackage(String packageName) throws ClassNotFoundException, SQLException
	{
		String where = "  , " + dbowner + "PSPACKAGEDEFN pk  where pk.PACKAGEROOT  = '" + packageName + "' and pk.PACKAGEID    = pc.OBJECTVALUE1    and pc.OBJECTVALUE1 = pk.PACKAGEROOT ";
		return getPeopleCodeContainers( where);
	}
	
	public static List<PeopleCodeContainer> getAllPeopleCodeContainers() throws ClassNotFoundException, SQLException
	{
		String where = " where (1=1) ";
		return getPeopleCodeContainers( where);
	}
	
	public static void writeToDirectoryTree( List<PeopleCodeContainer> containers, File rootDir) throws IOException, SQLException, ClassNotFoundException
	{	
		logger.info("Writing to directory tree " + rootDir);
		rootDir.mkdirs();
		DirTreePTmapper mapper= new DirTreePTmapper(rootDir);
		for (PeopleCodeContainer p: containers)
		{
			p.writeBytesToFile(mapper.getFile(p, "bin"));
			if (p instanceof JDBCPeopleCodeContainer)
				((JDBCPeopleCodeContainer) p).writeReferencesToFile(mapper.getFile(p, "references"));
		}
		logger.info("Finished writing to directory tree");
	}
		
	public static void writeProjectToDirectoryTree2( String project, File rootDir) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Retrieving bytecode for project " + project);
		List<PeopleCodeContainer> containers;
		containers = getPeopleCodeContainersForProject(props, project);
		writeToDirectoryTree(containers, rootDir);
	}
	
	public static void processProject( String projectName, ContainerProcessor processor) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Starting to write PeopleCode for project " + projectName );
		String whereClause = " , " + dbowner + "PSPROJECTITEM pi where  (pi.OBJECTVALUE1= pc.OBJECTVALUE1 and pi.OBJECTID1= pc.OBJECTID1) "
	    + " and ((pi.OBJECTVALUE2= pc.OBJECTVALUE2 and pi.OBJECTID2= pc.OBJECTID2 and pi.OBJECTVALUE3= pc.OBJECTVALUE3 and pi.OBJECTID3= pc.OBJECTID3 and pi.OBJECTVALUE4= pc.OBJECTVALUE4 and pi.OBJECTID4= pc.OBJECTID4)"
		+ "  or (pi.OBJECTTYPE  = 43 and pi.OBJECTVALUE3 = pc.OBJECTVALUE6))  "
		+ " and pi.PROJECTNAME='" + projectName + "' and pi.OBJECTTYPE in (8 , 9 ,39 ,40 ,42 ,43 ,44 ,46 ,47 ,48 ,58)";
		makeAndProcessContainers( whereClause, processor);
		logger.info("Finished writing .pcode files for project " + projectName);		
		processSQLforProject(projectName, processor); 		
		logger.info("Finished writing .sql files for project " + projectName);		
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
		public void process(PeopleCodeContainer p) throws IOException 
		{
			p.writeBytesToFile(mapper.getFile(p, "bin"));
			if (p instanceof JDBCPeopleCodeContainer)
				((JDBCPeopleCodeContainer) p).writeReferencesToFile(mapper.getFile(p, "references"));
		}
		public void processSQL(SQLobject sql) throws IOException {
			File sqlFile = mapper.getFileForSQL(sql.recName, "sql");
			logger.info("Creating " + sqlFile);
			FileWriter fw = new FileWriter(sqlFile);
//			dbedit.internal.parser.Formatter formatter = new Formatter();
//			sql = formatter.format(sql, 0, null, System.getProperty("line.separator"));
			fw.write(sql.sql);
			fw.close();
			File infoFile = mapper.getFileForSQL(sql.recName, "last_update");
			PrintWriter pw = new PrintWriter(infoFile);
			pw.println(sql.lastChangedBy);
			pw.println(ProjectReader.df2.format(sql.lastChanged));
			pw.close();
			countSQL++;
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
		public void process(PeopleCodeContainer p) throws IOException 
		{
			File f = mapper.getFile(p, extension);
			logger.info("Creating " + f);
			FileWriter w = new FileWriter(f);
			try {
				if (p.hasPlainPeopleCode()) // why decode the bytecode if we have the plain text...
					w.write(p.getPeopleCodeText());
				else
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
		public void processSQL(SQLobject sql) throws IOException 
		{
			File sqlFile = mapper.getFileForSQL(sql.recName, "sql");
			logger.info("Creating " + sqlFile);
			FileWriter fw = new FileWriter(sqlFile);
			fw.write(sql.sql);
			fw.close();
			if (sql.getLastChangedBy() != null && sql.lastChanged != null)
			{
				File infoFile = mapper.getFileForSQL(sql.recName, "last_update");
				PrintWriter pw = new PrintWriter(infoFile);
				pw.println(sql.lastChangedBy);
				pw.println(ProjectReader.df2.format(sql.lastChanged));
				pw.close();
			}
			countSQL++;			
		}		
	}
		
	public static void writeDecodedPPC( String whereClause, ContainerProcessor processor) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write decoded PeopleCode segments");
		makeAndProcessContainers( whereClause, processor);
		logger.info("Finished writing .pcode files");		
	}
	
	public static class DateSetter implements ParameterSetter
	{
		java.sql.Timestamp d;
		public DateSetter( java.sql.Timestamp _d) { d = _d; }
		public void setParameters(PreparedStatement ps) throws SQLException {
			ps.setTimestamp(1, d);			
		}
	}	
	
	public static void writeDecodedRecentPPC( java.sql.Timestamp fromDate, ContainerProcessor processor) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write decoded PeopleCode with time stamp after " + fromDate );
		FileWriter f = new FileWriter(lastTimeFile);
		f.write(ProjectReader.df2.format(new Date()));
		f.close();
		String whereClause = " where LASTUPDDTTM > ?";
		makeAndProcessContainers( whereClause, processor, new DateSetter(fromDate));
		logger.info("Finished writing .pcode files");		
	}

	public static void writeCustomizedPPC( ContainerProcessor processor) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write customized PeopleCode files ");
		String whereClause = " where pc.LASTUPDOPRID <> 'PPLSOFT'";
		makeAndProcessContainers( whereClause, processor);
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
	@SuppressWarnings("unchecked")
	public static void main(String[] a)
	{
		try {
			if (a.length == 0 || !a[0].startsWith("Process"))
				throw new IllegalArgumentException("First argument should be ProcessToFile or similar");			
			ContainerProcessorFactory factory = null;
			Class<ContainerProcessorFactory> factoryClass = null;
			if (a[0].equals("ProcessToFile"))
			{
				factoryClass = (Class<ContainerProcessorFactory>) Class.forName("decodepcode.FileProcessorFactory");
				File dir =	new File(".", "output");
				System.out.println("Output in " + dir.getAbsolutePath() );
			}
			else
				if (a[0].equals("ProcessToSVN"))
				{
						factoryClass = (Class<ContainerProcessorFactory>) 
							Class.forName("decodepcode.svn.SubversionProcessorFactory");
				}
				else
					throw new IllegalArgumentException("Don't have a processor class for " + a[0] );
			
			factory = (ContainerProcessorFactory) factoryClass.newInstance();
			factory.setParameters(props);
			ContainerProcessor processor = factory.getContainerProcessor();

			if (a.length > 2 && "since".equalsIgnoreCase(a[1]))
			{
				SimpleDateFormat sd = new SimpleDateFormat("yyyy/MM/dd");
				java.util.Date time = sd.parse(a[2]);
				java.sql.Timestamp d = new java.sql.Timestamp(time.getTime());
				writeDecodedRecentPPC(d, processor);
				processSQLsinceDate( d, processor);
				writeStats();
				return;
			}

			if (a.length > 2 && "since-days".equalsIgnoreCase(a[1]))
			{
				java.util.Date time = new Date();
				long days = Long.parseLong(a[2]);
				java.sql.Timestamp d = new java.sql.Timestamp(time.getTime() - 24 * 60 * 60 * 1000 * days);
				writeDecodedRecentPPC(d, processor);
				processSQLsinceDate( d, processor);
				writeStats();
				return;
			}
			if (a.length >= 2 && "since-last-time".equalsIgnoreCase(a[1]))
			{
				if (!lastTimeFile.exists())
				{
					logger.severe("Need file 'last-time.txt' to run with 'since-last-time' parameter");
					return;
				}
				BufferedReader br = new BufferedReader(new FileReader(lastTimeFile));
				String line = br.readLine();
				br.close();
				Date d;
				try {
					d = ProjectReader.df2.parse(line);
				} catch (ParseException e) {
					logger.severe("Found " + lastTimeFile + ", but can't parse its contents to a date/time");
					return;
				}
				logger.info("Processing objects modified since last time = " + line);
				writeDecodedRecentPPC(new Timestamp(d.getTime()), processor);
				processSQLsinceDate( new Timestamp(d.getTime()), processor);
				writeStats();
				return;				
			}
			
			if (a.length >= 2 && "custom".equalsIgnoreCase(a[1]))
			{
				writeCustomizedPPC(processor);
				processCustomSQLs( processor);
				writeStats();
				return;				
			}

			if (a.length >= 2 && a[1].toLowerCase().endsWith(".xml"))
			{
				ProjectReader p = new ProjectReader();
				p.setProcessor(processor);
				p.readProject( new File(a[1]));
				writeStats();
			}

			
			if (a.length == 2)
			{
				processProject(a[1], processor);
				writeStats();
				return;
			}
			
			System.err.println("Arguments: ProcessToXXX followed by project name, or 'since' + date (in yyyy/MM/dd format), or 'since-days' + #days, or 'since-last-time', or 'custom'");

		} catch (Exception e) {
			logger.severe(e.getMessage());
			e.printStackTrace();
		}
	}
	

}
