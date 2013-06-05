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
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;
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
	static String oprid = null;
	final static File lastTimeFile = new File("last-time.txt");
	private static Set<String> recsProcessed = new HashSet<String>(); // for SQL IDs 

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
	
	public static List<PeopleCodeObject> getPeopleCodeContainers(String whereClause, boolean queryAllConnections) throws ClassNotFoundException, SQLException
	{
		List<PeopleCodeObject> list = new ArrayList<PeopleCodeObject>();
		StoreInList s = new StoreInList(list, null);
		List<ContainerProcessor> processors = new ArrayList<ContainerProcessor>();
		processors.add(s);
		try {
			makeAndProcessContainers( whereClause, queryAllConnections, processors);
		} catch (IOException io) {}
		return list;
	}	
	
	public static Connection getJDBCconnection( String suffix) throws ClassNotFoundException, SQLException
	{
		logger.info("Getting JDBC connection");
		if (props.getProperty("driverClass" + suffix) != null)
			Class.forName(props.getProperty("driverClass" + suffix));
		else
			Class.forName(props.getProperty("driverClass"));
		Connection c = DriverManager.getConnection(props.getProperty("url" + suffix), 
				props.getProperty("user"+ suffix), 
				props.getProperty("password"+ suffix));
		if (c.isClosed())
			logger.severe("Could not open connection with suffix "+ suffix);
		return c;		
	}
	
	public interface ParameterSetter
	{
		public void setParameters( PreparedStatement ps) throws SQLException;
	}

	public static void makeAndProcessContainers( String whereClause, boolean queryAllConnections, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		makeAndProcessContainers( whereClause, queryAllConnections, processors, null);
	}
	
	public static void makeAndProcessContainers( 
				String whereClause, 
				boolean queryAllConnections,
				List<ContainerProcessor> processors, 
				ParameterSetter callback) throws ClassNotFoundException, SQLException, IOException
	{
		for (ContainerProcessor processor0: processors)
			processor0.aboutToProcess();
		boolean canAccessPSPCMTXT = false;
		if ("true".equalsIgnoreCase(props.getProperty("AlwaysDecode")))
			logger.info("NOT trying to read PSPCMTXT because of AlwaysDecode parameter");
		else
		{
			Statement st = null ;
			try {
				ContainerProcessor pc1 = processors.listIterator().next();
				st = pc1.getJDBCconnection().createStatement();
				st.executeQuery("select 'x' from "+ pc1.getDBowner() + "PSPCMTXT");
				canAccessPSPCMTXT = true;
				logger.info("Can read PSPCMTXT (tools >= 8.52)");
			} catch (SQLException e) {logger.info("Can NOT access PSPCMTXT:"+ e.getMessage()); }
			finally { if (st != null) st.close(); }
		}
		Set<String> processedKeys = new HashSet<String>();
		for (ContainerProcessor processor1: processors)
		{
			logger.info("Now determining what PeopleCode to process by querying environment " + processor1.getTag());
			String q = "select "+ KeySet.getList() + ", LASTUPDOPRID, LASTUPDDTTM from " 
				+ processor1.getDBowner() + "PSPCMPROG pc " + whereClause + " and pc.PROGSEQ=0";
			logger.info(q);
			PreparedStatement st0 =  processor1.getJDBCconnection().prepareStatement(q);
			if (callback != null)
				callback.setParameters(st0);
			ResultSet rs = st0.executeQuery();
			while (rs.next())
			{
				if (queryAllConnections)
				{
					JDBCPeopleCodeContainer.KeySet key = new KeySet(rs);
					if (processedKeys.contains(key.compositeKey()))
					{
						logger.info("Already processed key " + key.compositeKey() + "; skipping");
						continue;
					}
					processedKeys.add(key.compositeKey());
				}
				for (ContainerProcessor processor: processors)
				{
					JDBCPeopleCodeContainer c = new JDBCPeopleCodeContainer(processor.getJDBCconnection(), processor.getDBowner(), rs, canAccessPSPCMTXT);
					if (c.hasFoundPeopleCode())
					{
						processor.process(c);
						logger.fine("Completed JDBCPeopleCodeContainer for tag " + processor.getTag() );
					}
					else
						logger.fine("No PeopleCode found in environment "+ processor.getTag() + "; nothing to process");
				}
				countPPC++;
			}
			if (!queryAllConnections)
			{
				logger.info("Only processing Base environment");
				continue;
			}
		}
		for (ContainerProcessor processor0: processors)
			processor0.finishedProcessing();
	}
	public static String dbTypeXLAT( String dbType)
	{
		if (dbType == null || " ".equals(dbType)) return " ";
		if ("1".equals(dbType)) return	"DB2";
		if( "2".equals(dbType)) return	"Oracle";
		if( "3".equals(dbType)) return	"Informix";
		if( "4".equals(dbType)) return	"DB2_UNIX";
		if( "6".equals(dbType)) return	"Sybase";
		if( "7".equals(dbType)) return	"Microsoft";
		return dbType;
	}
	
	/*
	 * select 
d.LASTUPDOPRID, d.LASTUPDDTTM, 
td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT 
from PSSQLDEFN d, PSSQLTEXTDEFN td where d.SQLID=td.SQLID 
---and td.SQLID in (select OBJECTVALUE1 from PSPROJECTITEM where PROJECTNAME='TEST2')
	 */
	static void processSQLs( ResultSet rs, List<ContainerProcessor> processors) throws SQLException, IOException
	{
		for (ContainerProcessor processor: processors)
		{
			String q = 					"select td.SQLTEXT, d.LASTUPDDTTM, d.LASTUPDOPRID, td.MARKET from " 
					+ processor.getDBowner() + "PSSQLDEFN d, " 					
					+ processor.getDBowner() + "PSSQLTEXTDEFN td where d.SQLID=td.SQLID and td.SQLID = ?"
					+ " and td.MARKET=? and td.DBTYPE like ? and td.SQLTYPE=?";
			processor.setPs(processor.getJDBCconnection().prepareStatement(q));
		}
		while (rs.next())
		{
			String recName = rs.getString("SQLID");
			String dbType = rs.getString("DBTYPE");
			String market = rs.getString("MARKET");
			int sqlType = rs.getInt("SQLTYPE");
			//if (" ".equals(dbType))
			//	dbType = "%";
			String sqlKey = "" + sqlType + "-" + recName + "-" + rs.getString("MARKET") + "-" + dbType;
			if (recsProcessed.contains(sqlKey))
			{
				logger.info("Already processed SQL ID "+ sqlKey + "; skipping");
				continue;
			}
			recsProcessed.add(sqlKey);
			for (ContainerProcessor processor: processors)
			{
				processor.getPs().setString(1, recName);
				processor.getPs().setString(2, market);
				processor.getPs().setString(3, dbType);
				processor.getPs().setInt(4, sqlType);
				ResultSet rs2 = processor.getPs().executeQuery();
				if (rs2.next())
				{
					String sqlStr = rs2.getString("SQLTEXT");
					if (recName == null || sqlStr == null)
						continue;
					Timestamp d = rs2.getTimestamp("LASTUPDDTTM");
					Date date = d == null? new Date(0) : new Date(d.getTime());
					SQLobject sql = new SQLobject(sqlType, recName.trim(), 
							sqlStr.trim(), 
							rs2.getString("LASTUPDOPRID"),
							date,
							rs2.getString("MARKET"),
							dbTypeXLAT(dbType));
					processor.processSQL(sql);
				}
				else
					logger.info("SQLID '" + recName + "' not found in environment " + processor.getTag());
			}
			countSQL++;
		}
	}

	public static void processSQLforProject(String projectName, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td, " 
				+ dbowner + "PSPROJECTITEM pi  where d.SQLID=td.SQLID and d.SQLID=pi.OBJECTVALUE1 and pi.OBJECTID1=65 and pi.OBJECTVALUE2=td.SQLTYPE and pi.PROJECTNAME='" + projectName + "'";  
		Statement st0 =  dbconn.createStatement();
		logger.info(q);
		ResultSet rs = st0.executeQuery(q);		
		processSQLs(rs, processors);
		st0.close();
	}
	
	
	public static void processSQLsinceDate( java.sql.Timestamp date, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		// query all environments with the query on LASTUPDDTTM" 
		for (ContainerProcessor processor: processors)
		{
			String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
				+ processor.getDBowner() + "PSSQLDEFN d, " + processor.getDBowner()+ "PSSQLTEXTDEFN td " 
					+ " where d.SQLID=td.SQLID and d.LASTUPDDTTM >= ?";
			if (oprid != null)
				q += " and d.LASTUPDOPRID = '" + oprid + "'";
			PreparedStatement st0 =  processor.getJDBCconnection().prepareStatement(q);
			st0.setTimestamp(1, date);
			logger.info(q);
			ResultSet rs = st0.executeQuery();		
			processSQLs(rs, processors);
			st0.close();
		}
	}

	/*
	public static void writeCustomSQLtoFile(File baseDir) throws ClassNotFoundException, SQLException, IOException
	{
		processCustomSQLs( new WriteDecodedPPCtoDirectoryTree(baseDir));
	}
	*/
	
	public static void processCustomSQLs(List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		String q = "select d.SQLID, d.LASTUPDOPRID, d.LASTUPDDTTM, td.SQLTYPE, td.MARKET, td.DBTYPE, td.SQLTEXT from "
			+ dbowner + "PSSQLDEFN d, " + dbowner + "PSSQLTEXTDEFN td " 
				+ " where d.SQLID=td.SQLID and d.LASTUPDOPRID <> 'PPLSOFT'";  
		if (oprid != null)
			q += " and d.LASTUPDOPRID = '" + oprid + "'";
		PreparedStatement st0 =  dbconn.prepareStatement(q);
		logger.info(q);
		ResultSet rs = st0.executeQuery();		
		processSQLs(rs, processors);
		st0.close();
	}
		
	public static List<PeopleCodeObject> getPeopleCodeContainersForProject(Properties props, String projectName) throws ClassNotFoundException, SQLException
	{
		String where = " , " + dbowner + "PSPROJECTITEM pi where  (pi.OBJECTVALUE1= pc.OBJECTVALUE1 and pi.OBJECTID1= pc.OBJECTID1) "
		    + " and ((pi.OBJECTVALUE2= pc.OBJECTVALUE2 and pi.OBJECTID2= pc.OBJECTID2 and pi.OBJECTVALUE3= pc.OBJECTVALUE3 and pi.OBJECTID3= pc.OBJECTID3 and pi.OBJECTVALUE4= pc.OBJECTVALUE4 and pi.OBJECTID4= pc.OBJECTID4)"
			+ "  or (pi.OBJECTTYPE  = 43 and pi.OBJECTVALUE3 = pc.OBJECTVALUE6))  "
			+ " and pi.PROJECTNAME='" + projectName + "' and pi.OBJECTTYPE in (8 , 9 ,39 ,40 ,42 ,43 ,44 ,46 ,47 ,48 ,58)";
		return getPeopleCodeContainers( where, false);
	}
	
	public static Properties readProperties() throws IOException
	{
		Properties props= new Properties();
		props.load(new FileInputStream("DecodePC.properties"));
		dbowner = props.getProperty("dbowner");
		dbowner = dbowner == null? "" : dbowner + ".";
		return props;
	}
	
	public static List<PeopleCodeObject> getPeopleCodeContainersForApplicationPackage(String packageName) throws ClassNotFoundException, SQLException
	{
		String where = "  , " + dbowner + "PSPACKAGEDEFN pk  where pk.PACKAGEROOT  = '" + packageName + "' and pk.PACKAGEID    = pc.OBJECTVALUE1    and pc.OBJECTVALUE1 = pk.PACKAGEROOT ";
		return getPeopleCodeContainers( where, false);
	}
	
	public static List<PeopleCodeObject> getAllPeopleCodeContainers() throws ClassNotFoundException, SQLException
	{
		String where = " where (1=1) ";
		return getPeopleCodeContainers( where, false);
	}
	
	public static void writeToDirectoryTree( List<PeopleCodeObject> containers, File rootDir) throws IOException, SQLException, ClassNotFoundException
	{	
		logger.info("Writing to directory tree " + rootDir);
		rootDir.mkdirs();
		DirTreePTmapper mapper= new DirTreePTmapper(rootDir);
		for (PeopleCodeObject p: containers)
		{
			if (p instanceof JDBCPeopleCodeContainer)
			{
				((JDBCPeopleCodeContainer) p).writeBytesToFile(mapper.getFile(p, "bin"));
				((JDBCPeopleCodeContainer) p).writeReferencesToFile(mapper.getFile(p, "references"));
			}
		}
		logger.info("Finished writing to directory tree");
	}
		
	public static void writeProjectToDirectoryTree2( String project, File rootDir) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Retrieving bytecode for project " + project);
		List<PeopleCodeObject> containers;
		containers = getPeopleCodeContainersForProject(props, project);
		writeToDirectoryTree(containers, rootDir);
	}
	
	public static void processProject( String projectName, List<ContainerProcessor> processors) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Starting to write PeopleCode for project " + projectName );
		/* for most PeopleCode object types, the 4 OBJECTVALUE fields match in PSPROJECTITEM and in PSPCMPROG, but
		 * App Engine ppc (43) and Component Record Field ppc (48) are a bit different, as their PCMPROG uses more keys fit in the project definition
		 * Also, the third key of app package ppc (58) does not seem to be used in the project definition.
		 */
		String concat = processors.get(0).getJDBCconnection().getMetaData().getDatabaseProductName().toLowerCase().indexOf("sql server") >= 0? 
				"+" 
			: 
				"||";
		
		/*String whereClause = " , " + dbowner + "PSPROJECTITEM pi where  (pi.OBJECTVALUE1= pc.OBJECTVALUE1 and pi.OBJECTID1= pc.OBJECTID1) "
	    + " and ((pi.OBJECTVALUE2= pc.OBJECTVALUE2 and pi.OBJECTID2 = pc.OBJECTID2"
	    + "   and (pi.OBJECTTYPE=58 or (pi.OBJECTVALUE3= pc.OBJECTVALUE3 and pi.OBJECTID3= pc.OBJECTID3))) "
		+ " and ((pi.OBJECTVALUE4= pc.OBJECTVALUE4 and pi.OBJECTID4= pc.OBJECTID4)"
		+ "  or (pi.OBJECTTYPE = 48 and pi.OBJECTVALUE4 like (pc.OBJECTVALUE4 " + concat + " '%' " + concat + "  pc.OBJECTVALUE5))) "
		+ "  or (pi.OBJECTTYPE = 43 and pi.OBJECTVALUE2 like pc.OBJECTVALUE2 " + concat + " '%' and pi.OBJECTVALUE3 = pc.OBJECTVALUE6))  "
		+ " and pi.PROJECTNAME='" + projectName + "' and pi.OBJECTTYPE in (8, 9, 39 ,40 ,42 ,43 ,44 ,46 ,47 ,48 ,58)";
		*/
		String whereClause = " , " + dbowner + "PSPROJECTITEM pi where  " 
				+ " pi.OBJECTID1 = pc.OBJECTID1 AND pi.OBJECTVALUE1 = pc.OBJECTVALUE1 and ("
				 +"(pi.OBJECTTYPE = 58 AND ( ( pi.OBJECTID3 = 0 AND pi.OBJECTID2 = pc.OBJECTID2 AND pi.OBJECTVALUE2 = pc.OBJECTVALUE2 ) OR ( pi.OBJECTID3 <> 0 AND pi.OBJECTID4 = 0 AND pi.OBJECTID2 = pc.OBJECTID2 AND pi.OBJECTVALUE2 = pc.OBJECTVALUE2 AND pi.OBJECTID3 = pc.OBJECTID3 AND pi.OBJECTVALUE3 = pc.OBJECTVALUE3 ) OR ( pi.OBJECTID3 <> 0 AND pi.OBJECTID4 <> 0 AND pi.OBJECTID2 = pc.OBJECTID2 AND pi.OBJECTVALUE2 = pc.OBJECTVALUE2 AND pi.OBJECTID3 = pc.OBJECTID3 AND pi.OBJECTVALUE3 = pc.OBJECTVALUE3 AND pi.OBJECTID4 = pc.OBJECTID4 AND pi.OBJECTVALUE4 = pc.OBJECTVALUE4 ))) or "
				 +"   (pi.OBJECTTYPE = 48 and pi.OBJECTVALUE4 like (pc.OBJECTVALUE4 " + concat + " '%' " + concat + "  pc.OBJECTVALUE5)) OR"
				 +"   (pi.OBJECTTYPE = 43 and pi.OBJECTVALUE2 like pc.OBJECTVALUE2 " + concat + " '%' and pi.OBJECTVALUE3 = pc.OBJECTVALUE6)  OR "
				+   " ( pi.OBJECTTYPE NOT IN (43,48,58) AND pi.OBJECTID2 = pc.OBJECTID2 AND pi.OBJECTVALUE2 = pc.OBJECTVALUE2 AND pi.OBJECTID3 = pc.OBJECTID3 AND pi.OBJECTVALUE3 = pc.OBJECTVALUE3 AND pi.OBJECTID4 = pc.OBJECTID4 AND pi.OBJECTVALUE4 = pc.OBJECTVALUE4) "				
				+ ") and pi.PROJECTNAME='" + projectName + "' and pi.OBJECTTYPE in (8, 9, 39 ,40 ,42 ,43 ,44 ,46 ,47 ,48 ,58)";
		makeAndProcessContainers( whereClause, false, processors);
		logger.info("Finished writing .pcode files for project " + projectName);		
		processSQLforProject(projectName, processors); 		
		logger.info("Finished writing .sql files for project " + projectName);		
	}

	
	public static class WriteToDirectoryTree extends ContainerProcessor
	{
		String dBowner;
		Connection JDBCconnection;
		private File root;
		
		PToolsObjectToFileMapper mapper;
		WriteToDirectoryTree( PToolsObjectToFileMapper _mapper)
		{
			mapper = _mapper;
		}
		WriteToDirectoryTree( File rootDir)
		{
			this( new DirTreePTmapper(rootDir));
			root = rootDir;
		}
		public void process(PeopleCodeObject p) throws IOException 
		{
			if (p instanceof JDBCPeopleCodeContainer)
			{
				((JDBCPeopleCodeContainer) p).writeBytesToFile(mapper.getFile(p, "bin"));
				((JDBCPeopleCodeContainer) p).writeReferencesToFile(mapper.getFile(p, "references"));
			}
		}
		public void processSQL(SQLobject sql) throws IOException {
			File sqlFile = mapper.getFileForSQL(sql, "sql");
			logger.fine("Creating " + sqlFile);
			FileWriter fw = new FileWriter(sqlFile);
//			dbedit.internal.parser.Formatter formatter = new Formatter();
//			sql = formatter.format(sql, 0, null, System.getProperty("line.separator"));
			fw.write(sql.sql);
			fw.close();
			File infoFile = mapper.getFileForSQL(sql, "last_update");
			PrintWriter pw = new PrintWriter(infoFile);
			pw.println(sql.lastChangedBy);
			pw.println(ProjectReader.df2.format(sql.lastChanged));
			pw.close();
			countSQL++;
		}
		public String getDBowner() {
			return dBowner;
		}
		public void setDBowner(String dBowner) {
			this.dBowner = dBowner;
		}
		public Connection getJDBCconnection() {
			return JDBCconnection;
		}
		public void setJDBCconnection(Connection jDBCconnection) {
			JDBCconnection = jDBCconnection;
		}
		@Override
		public void aboutToProcess() {
			if (root != null)
				System.out.println("Output in " + root.getAbsolutePath() );			
		}		
	}
	
	static void writeToDirectoryTree( String whereClause, boolean queryAllConnections, File rootDir) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write bin/ref files to directory tree " + rootDir);
		List<ContainerProcessor> processors = new ArrayList<ContainerProcessor>();
		processors.add(new WriteToDirectoryTree(rootDir));
		makeAndProcessContainers( whereClause, queryAllConnections, processors);
		logger.info("Finished writing bin/ref files");
	}

	public static void writeAllPPCtoDirectoryTree( File rootDir) throws IOException, SQLException, ClassNotFoundException
	{
		logger.info("Retrieving all PeopleCode bytecode in database" );
		writeToDirectoryTree(" where 1=1 ", false, rootDir);
	}

	public static class WriteDecodedPPCtoDirectoryTree extends ContainerProcessor
	{
		PToolsObjectToFileMapper mapper;
		String extension;
		File root;
		
		PeopleCodeParser parser = new PeopleCodeParser();
		
		public WriteDecodedPPCtoDirectoryTree( PToolsObjectToFileMapper _mapper, String _extension)
		{
			mapper = _mapper;
			if (mapper instanceof DirTreePTmapper)
				root = ((DirTreePTmapper) mapper).rootDir;
			extension = _extension;
		}
		public WriteDecodedPPCtoDirectoryTree( File rootDir, String _extension)
		{
			this( new DirTreePTmapper(rootDir), _extension);
			root = rootDir;
		}
		public WriteDecodedPPCtoDirectoryTree( File rootDir)
		{
			this(rootDir, "pcode");
		}
		public void process(PeopleCodeObject p) throws IOException 
		{
			File f = mapper.getFile(p, extension);
			logger.fine("Creating " + f);
			FileWriter w = new FileWriter(f);
			try {
				if (p.hasPlainPeopleCode()) // why decode the bytecode if we have the plain text...
					w.write(p.getPeopleCodeText());
				else
					parser.parse(((PeopleCodeContainer) p), w);
				w.close();
				Date lastUpdt = p.getLastChangedDtTm();
				if (lastUpdt != null)
				{
					File infoFile = mapper.getFile(p, "last_update");
					PrintWriter pw = new PrintWriter(infoFile);
					pw.println(p.getLastChangedBy());
					pw.println(ProjectReader.df2.format(lastUpdt));
				pw.close();			
				}
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
			File sqlFile = mapper.getFileForSQL(sql, "sql");
			logger.info("Creating " + sqlFile);
			FileWriter fw = new FileWriter(sqlFile);
			fw.write(sql.sql);
			fw.close();
			if (sql.getLastChangedBy() != null && sql.lastChanged != null)
			{
				File infoFile = mapper.getFileForSQL(sql, "last_update");
				PrintWriter pw = new PrintWriter(infoFile);
				pw.println(sql.lastChangedBy);
				pw.println(ProjectReader.df2.format(sql.lastChanged));
				pw.close();
			}
			countSQL++;			
		}
		@Override
		public void aboutToProcess() {
			if (root != null)
				System.out.println("Output in " + root.getAbsolutePath() );			
			
		}
		public PToolsObjectToFileMapper getMapper() {
			return mapper;
		}		
	}
		
	public static void writeDecodedPPC( String whereClause, List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write decoded PeopleCode segments");
		makeAndProcessContainers( whereClause, false, processors);
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
	
	public static void writeDecodedRecentPPC( java.sql.Timestamp fromDate, 
			List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write decoded PeopleCode with time stamp after " + fromDate );
		if (oprid == null)
		{
			FileWriter f = new FileWriter(lastTimeFile);
			f.write(ProjectReader.df2.format(new Date()));
			f.close();
		}
		String whereClause = " where LASTUPDDTTM > ?";
		if (oprid != null)
			whereClause += " and LASTUPDOPRID = '" + oprid + "'";

		// with queryAllConnections = true, so that all environments are tracked with this query:
		makeAndProcessContainers( whereClause, true, processors, new DateSetter(fromDate));
		logger.info("Finished writing .pcode files");		
	}

	public static void writeCustomizedPPC( List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		logger.info("Starting to write customized PeopleCode files ");
		String whereClause = " where pc.LASTUPDOPRID <> 'PPLSOFT'";
		if (oprid != null)
			whereClause += " and pc.LASTUPDOPRID = '" + oprid + "'";
		makeAndProcessContainers( whereClause, false, processors);
		logger.info("Finished writing .pcode files");		
	}	
	
	static void writeStats()
	{
		System.out.println("Ready; processed "+ countPPC + " PeopleCode segment(s), and " + countSQL + " SQL definition(s)");		
	}
	
	@SuppressWarnings("unchecked")
	static ContainerProcessorFactory getContainerProcessorFactory( String type) throws ClassNotFoundException, InstantiationException, IllegalAccessException
	{
		Class<ContainerProcessorFactory> factoryClass = null;
		if ("ProcessToFile".equals(type))
		{
			factoryClass = (Class<ContainerProcessorFactory>) Class.forName("decodepcode.FileProcessorFactory");
		}
		else
		if ("ProcessToSVN".equals(type))
		{
				factoryClass = (Class<ContainerProcessorFactory>) 
					Class.forName("decodepcode.svn.SubversionProcessorFactory");
		}
		else
		if ("ProcessToGit".equals(type))
		{
				factoryClass = (Class<ContainerProcessorFactory>) 
					Class.forName("decodepcode.git.GitProcessorFactory");
		}
		else
		if ("ProcessBinToFile".equals(type))
		{
			factoryClass = (Class<ContainerProcessorFactory>) 
				Class.forName("decodepcode.BinFileProcessorFactory");
		}
		else
				throw new IllegalArgumentException("Don't have a processor class for " + type );
		return (ContainerProcessorFactory) factoryClass.newInstance();
		
	}
	
	public static void doTest(List<ContainerProcessor> processors) throws ClassNotFoundException, SQLException, IOException
	{
		String whereClause = " where OBJECTVALUE1 = 'USER_PROFILE' and OBJECTID2 = 12";

		makeAndProcessContainers( whereClause, true, processors);
		logger.info("Finished writing .pcode files");		
		
	}
	
	/**
	 * Run from command line:
	 *  Arguments: project name, or 'since' + date (in yyyy/MM/dd format), or 'since-days' + #days, or 'custom'"
	 * @param a
	 */	
	public static void main(String[] a)
	{
		try {
			if (a.length == 0 || !a[0].startsWith("Process"))
				throw new IllegalArgumentException("First argument should be ProcessToFile or similar");			
			ContainerProcessorFactory factory = null;

			factory = getContainerProcessorFactory(a[0]);
			factory.setParameters(props, "");
			List<ContainerProcessor> processors = new ArrayList<ContainerProcessor>();
			ContainerProcessor processor = factory.getContainerProcessor();
			processor.setTag("Base");
			
			processors.add(processor);

			boolean inputIsPToolsProject = a.length >= 2 && a[1].toLowerCase().endsWith(".xml");
			for (Object key1: props.keySet())
			{
				String key = (String) key1;
				if (key.toLowerCase().startsWith("process"))
				{
					String suffix = key.substring("process".length());
					String processType = props.getProperty(key);
					try 
					{
						ContainerProcessorFactory factory1 = getContainerProcessorFactory(processType);
						factory1.setParameters(props, suffix);
						ContainerProcessor processor1 = factory1.getContainerProcessor();
						if (!inputIsPToolsProject)
						{
							processor1.setJDBCconnection(getJDBCconnection(suffix));
							String schema = props.getProperty("dbowner" + suffix);
							schema = schema == null || schema.length() == 0? "" : schema + ".";
							processor1.setDBowner(schema);
						}
						processor1.setTag(suffix);
						processors.add(processor1);
					} catch (IllegalArgumentException ex)
					{
						logger.severe("Process type for "+ key + " not known - skipping");
					}
					catch (SQLException ex)
					{
						logger.severe(ex.getMessage());
						logger.severe("JDBC connection parameters for processor with suffix '" + suffix + "' absent or invalid");
					}
				}
			}						
			
			if (inputIsPToolsProject)
			{
				String target = (a.length >=3) ? a[2] : "Base";
				ProjectReader p = new ProjectReader();
				boolean found = false;
				for (ContainerProcessor processor1: processors)
					if (target.equals(processor1.getTag()))
					{
						File f = new File(a[1]);
						System.out.println("Reading PeopleTools project " + f.getName() + ("Base".equals(target)? "" : ", processing for environment " + target));
						found = true;
						p.setProcessor(processor1);
						p.readProject( f);
						writeStats();
					}
					if (!found)
						logger.severe("There is no target environment labeled '" + target + "' - file not processed");
					return;
			}

			// not reading project, so need to have JDBC Connection to read bytecode
			dbconn = getJDBCconnection("");
			processor.setJDBCconnection(dbconn);
			processor.setDBowner(dbowner);
			
/*
			if (a.length >= 2 && "TEST".equalsIgnoreCase(a[1]))
			{
				doTest(processors);
				return;
			}
*/			
			if (a.length >= 3 && "OPRID".equalsIgnoreCase(a[a.length-2]))
				oprid = a[a.length-1];
			
			if (a.length > 2 && "since".equalsIgnoreCase(a[1]))
			{
				SimpleDateFormat sd = new SimpleDateFormat("yyyy/MM/dd");
				java.util.Date time = sd.parse(a[2]);
				java.sql.Timestamp d = new java.sql.Timestamp(time.getTime());
				writeDecodedRecentPPC(d, processors);
				processSQLsinceDate( d, processors);
				writeStats();
				return;
			}

			if (a.length > 2 && "since-days".equalsIgnoreCase(a[1]))
			{
				java.util.Date time = new Date();
				long days = Long.parseLong(a[2]);
				java.sql.Timestamp d = new java.sql.Timestamp(time.getTime() - 24 * 60 * 60 * 1000 * days);
				writeDecodedRecentPPC(d, processors);
				processSQLsinceDate( d, processors);
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
				String timeOffset = null;
				try {
					d = ProjectReader.df2.parse(line);
					timeOffset = props.getProperty("last-time-offset");
					if (timeOffset != null)
						d = new Date(d.getTime() - Long.parseLong(timeOffset) * 60 * 1000);
				} catch (ParseException e) {
					logger.severe("Found " + lastTimeFile + ", but can't parse its contents to a date/time: " + e.getMessage());
					return;
				}
				logger.info("Processing objects modified since last time = " + line 
						+ (timeOffset == null? "" : "( with a " + timeOffset + "-min offset)"));
				writeDecodedRecentPPC(new Timestamp(d.getTime()), processors);
				processSQLsinceDate( new Timestamp(d.getTime()), processors);
				writeStats();
				return;				
			}
			
			if (a.length >= 2 && "custom".equalsIgnoreCase(a[1]))
			{
				writeCustomizedPPC(processors);
				processCustomSQLs( processors);
				writeStats();
				return;				
			}
	
			if (a.length == 2)
			{
				processProject(a[1], processors);
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
