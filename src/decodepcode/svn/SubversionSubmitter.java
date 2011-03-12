package decodepcode.svn;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.StringWriter;
//import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.logging.Logger;

import org.tmatesoft.svn.core.SVNErrorCode;
import org.tmatesoft.svn.core.SVNErrorMessage;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNNodeKind;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.fs.FSRepositoryFactory;
import org.tmatesoft.svn.core.internal.io.svn.SVNRepositoryFactoryImpl;
import org.tmatesoft.svn.core.io.ISVNEditor;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;
import org.tmatesoft.svn.core.io.diff.SVNDeltaGenerator;
import org.tmatesoft.svn.core.wc.SVNWCUtil;

//import decodepcode.Controller;
import decodepcode.DirTreePTmapper;
import decodepcode.PToolsObjectToFileMapper;
import decodepcode.PeopleCodeParser;
import decodepcode.ProjectReader;
import decodepcode.SQLobject;
//import decodepcode.Controller.WriteDecodedPPCtoDirectoryTree;
import decodepcode.ContainerProcessor;

/**
 * 
 * Uses the SVNKit library to commit .pcode and .sql files to Subversion.
 *
 */
public class SubversionSubmitter 
{
	static Logger logger = Logger.getLogger(SubversionSubmitter.class.getName());

    private static void addDirPath(SVNRepository repository, String dirPath) throws SVNException
    {	
    	logger.fine("addDirPath: "+ dirPath);
    	if (dirPath.endsWith("/"))
    		dirPath = dirPath.substring(0, dirPath.length() -1 );
    	if (!(dirPath.startsWith("/trunk") || dirPath.startsWith("/tags") || dirPath.startsWith("/branches")))
    		throw new IllegalArgumentException("Expected absolute path (/trunk, /tags or /branches); got " + dirPath);
        SVNNodeKind nodeKind = repository.checkPath( dirPath, -1);
        if (nodeKind == SVNNodeKind.DIR) {
        	return;
        } else if (nodeKind == SVNNodeKind.FILE) {
            SVNErrorMessage err = SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry at URL ''{0}'' is a file while directory was expected", dirPath);
            throw new SVNException(err);
        }
    	int lastSlash = dirPath.lastIndexOf("/");
    	if (lastSlash < 1)
    		return;
    	String parentDir = dirPath.substring(0, lastSlash);
    	addDirPath(repository, parentDir);
    	ISVNEditor editor = repository.getCommitEditor("create path", null);
        editor.openRoot(-1);
        editor.addDir(dirPath, null, -1);
        editor.closeDir();
        editor.closeDir();
        editor.closeEdit();
    }
    
    private static void addFile( SVNRepository repository, 
    							String filePath, 
    							String commitStr,
    							byte[] data) throws SVNException	    
    {	
    	int lastSlash = filePath.lastIndexOf("/");
    	if (lastSlash < 1)
    		throw new IllegalArgumentException("Expected file name with directory path, got " + filePath);
    	String dirPath = filePath.substring(0, lastSlash);
    	addDirPath(repository, dirPath);
        SVNNodeKind nodeKind = repository.checkPath( filePath, -1);
        if (nodeKind == SVNNodeKind.DIR) 
            throw new SVNException(SVNErrorMessage.create(SVNErrorCode.UNKNOWN, "Entry at URL ''{0}'' is a directory while a file was expected", filePath));
        boolean doesNotExist = nodeKind == SVNNodeKind.NONE; 
    	ISVNEditor editor = repository.getCommitEditor( commitStr, null);
        editor.openRoot(-1);
        if (doesNotExist)
        {
        	logger.info("Creating file " + filePath);
        	editor.addFile(filePath, null, -1);
        }
        else
        {
        	logger.info("Updating file " + filePath);
	        editor.openDir(dirPath, -1);
	        editor.openFile(filePath, -1);		        
        }
        editor.applyTextDelta(filePath, null);
        SVNDeltaGenerator deltaGenerator = new SVNDeltaGenerator();
        String checksum = deltaGenerator.sendDelta(filePath, new ByteArrayInputStream(data), editor, true);
        editor.closeFile(filePath, checksum);
        editor.closeDir();
        editor.closeEdit();
    }

    static interface AuthManagerMapper
    {
    	ISVNAuthenticationManager getAuthManager( String userName);
    }
    static class FixedAuthManagerMapper implements AuthManagerMapper
    {
    	HashMap<String, ISVNAuthenticationManager> map = new HashMap<String, ISVNAuthenticationManager>();
    	ISVNAuthenticationManager defaultCredentials;
    	
    	public ISVNAuthenticationManager getAuthManager( String userName)
    	{
    		ISVNAuthenticationManager m = map.get(userName); 
    		return  m == null? defaultCredentials : m;
    	}
    	void addCredentials( String pToolUserName, String svnUserName, String svnPassword)
    	{
    		ISVNAuthenticationManager m = SVNWCUtil.createDefaultAuthenticationManager(svnUserName, svnPassword);
    		if (map.size() == 0)
    			defaultCredentials = m;
    		map.put(pToolUserName, m);
    	}
    }
    
    public static class SubversionContainerProcessor implements ContainerProcessor
	{
		SVNRepository repository;
		String basePath;
		PToolsObjectToFileMapper mapper;
		PeopleCodeParser parser = new PeopleCodeParser();
		AuthManagerMapper authMapper;
		
		SubversionContainerProcessor( SVNURL url, 
				String _basePath, 
				PToolsObjectToFileMapper _mapper, 
				AuthManagerMapper _authMapper) throws SVNException
		{
			repository = SVNRepositoryFactory.create(url);
			basePath = _basePath;
			mapper = _mapper;
			authMapper = _authMapper;
			System.out.println("Submitting PeopleCode and SQL definitions to " + url + basePath);
		}

		public void process(decodepcode.PeopleCodeContainer c) throws IOException 
		{
			StringWriter w = new StringWriter();
			if (c.hasPlainPeopleCode()) // why decode the bytecode if we have the plain text...
				w.write(c.getPeopleCodeText());
			else
				parser.parse(c, w);
			String path = basePath + mapper.getPath(c, "pcode");
			try {
				ISVNAuthenticationManager user = authMapper.getAuthManager(c.getLastChangedBy());
				if (user != null)
					repository.setAuthenticationManager(user);
				addFile(repository, path, 
					"Saved at " + ProjectReader.df2.format(c.getLastChangedDtTm()) + " by " + c.getLastChangedBy(), 
					w.toString().getBytes());
			} catch (SVNException se)
			{
				IOException e = new IOException("Error submitting pcode to Subversion");
				e.initCause(se);
				throw e; 				
			}
		}

		public void processSQL(SQLobject sql) throws IOException 
		{
			String path = basePath + mapper.getPathForSQL(sql.getRecName(), "sql");
			try {
				ISVNAuthenticationManager user = authMapper.getAuthManager(sql.getLastChangedBy());
				if (user != null)
					repository.setAuthenticationManager(user);
				if (sql.getLastChangedDtTm() != null && sql.getLastChangedBy() != null)
				addFile(repository, path, 
					"Saved at " + ProjectReader.df2.format(sql.getLastChangedDtTm()) + " by " + sql.getLastChangedBy(), 
					sql.getSql().getBytes());
			} catch (SVNException se)
			{
				IOException e = new IOException("Error submitting pcode to Subversion");
				e.initCause(se);
				throw e; 				
			}
		}		
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) 
	{
		try {
			FixedAuthManagerMapper authMapper = new FixedAuthManagerMapper();
			authMapper.addCredentials("PPLTOOLS" , "harry", "secret");
			authMapper.addCredentials("VP1", "sally", "secret");
			SubversionContainerProcessor processor = new SubversionContainerProcessor(
					SVNURL.parseURIEncoded("svn://192.168.1.4/project1"), 
					"/trunk/PeopleCode", 
					new DirTreePTmapper(), 
					authMapper);
			logger.info("Starting to commit to Subversion");
/*			
			SimpleDateFormat sd = new SimpleDateFormat("yyyy/MM/dd");
			java.util.Date time = sd.parse("2010/06/01");
			java.sql.Timestamp d = new java.sql.Timestamp(time.getTime());
			String whereClause = " where LASTUPDDTTM > ?";
			decodepcode.Controller.makeAndProcessContainers( whereClause, processor, new decodepcode.Controller.DateSetter(d));
			logger.info("Committed PeopleCode segments; now processing SQLs");
			decodepcode.Controller.processSQLsinceDate( d, processor);
			*/
			decodepcode.Controller.processProject("TEST2", processor);
			logger.info("Finished");		

				
			} catch (Exception e) {
				e.printStackTrace();
			} 
	}

	public static void setUpSVNKit()
	{
        /*
         * For using over http:// and https://
         */
        DAVRepositoryFactory.setup();
        /*
         * For using over svn:// and svn+xxx://
         */
        SVNRepositoryFactoryImpl.setup();
        
        /*
         * For using over file:///
         */
        FSRepositoryFactory.setup();

	
	}
	
	static 
	{
		setUpSVNKit();
	}
}
