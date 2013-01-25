package decodepcode.git;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.logging.Logger;

import org.eclipse.jgit.api.AddCommand;
import org.eclipse.jgit.api.CommitCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.UnmergedPathsException;

import decodepcode.ContainerProcessor;
import decodepcode.PToolsObjectToFileMapper;
import decodepcode.PeopleCodeParser;
import decodepcode.ProjectReader;
import decodepcode.SQLobject;
import decodepcode.git.GitProcessorFactory.GitUser;

/* Submit to local Git repository with JGit */
public class GitSubmitter 
{
		static Logger logger = Logger.getLogger(GitSubmitter.class.getName());
		File gitWorkDir;
		Git git;
		HashMap<String, GitUser> userMap;
		
	    private void addFile(	String psoft_user,  
	    							String filePath, 
	    							String commitStr,
	    							byte[] data) throws UnmergedPathsException, GitAPIException, IOException 	    
	    {	
	    	int lastSlash = filePath.lastIndexOf("/");
	    	if (lastSlash < 1)
	    		throw new IllegalArgumentException("Expected file name with directory path, got " + filePath);
	    	String dirPath = filePath.substring(0, lastSlash), name = filePath.substring(lastSlash + 1);
		    String path1 = dirPath.replace("/", System.getProperty("file.separator"));
		    File dir1 = new File(gitWorkDir, path1);
		    dir1.mkdirs();
			File myfile = new File(dir1, name);
			FileOutputStream os = new FileOutputStream(myfile);
			os.write(data);
			os.close();
			AddCommand add = git.add();
			add.addFilepattern(filePath).call();
			GitUser user = userMap.get(psoft_user);
			if (user == null)
				user = userMap.get("default");
			CommitCommand commit = git.commit();
			commit.setMessage(commitStr).setAuthor(user.user, user.email).setCommitter("Decode Peoplecode", "nobody@dummy.org").call();
	    }

	    
	    public class GitContainerProcessor extends ContainerProcessor
		{	    	
			String basePath;
			PToolsObjectToFileMapper mapper;
			PeopleCodeParser parser = new PeopleCodeParser();
			
			GitContainerProcessor(
					HashMap<String, GitUser> _userMap,
					File gitDir,
					String _basePath, 
					PToolsObjectToFileMapper _mapper) throws IOException 
			{
				basePath = _basePath;
				mapper = _mapper;
			    git = Git.open(gitDir);
			    gitWorkDir = gitDir;
			    userMap = _userMap ;
				System.out.println("Submitting PeopleCode and SQL definitions to " + gitDir + basePath);
			}

			public void process(decodepcode.PeopleCodeObject c) throws IOException 
			{
				StringWriter w = new StringWriter();
				if (c.hasPlainPeopleCode()) // why decode the bytecode if we have the plain text...
					w.write(c.getPeopleCodeText());
				else
				{
					parser.parse(((decodepcode.PeopleCodeContainer) c), w);
				}
				String path = basePath + mapper.getPath(c, "pcode");
				try {
					addFile( c.getLastChangedBy(),
							path, 
						"Saved at " + ProjectReader.df2.format(c.getLastChangedDtTm()) + " by " + c.getLastChangedBy()
						+ (c.getSource() == null? "" : " (source: " + c.getSource() + ")")  , 
						w.toString().getBytes() );
				}  catch (UnmergedPathsException se) {
					IOException e = new IOException("Error submitting pcode to Git");
					e.initCause(se);
					throw e; 				
				} catch (GitAPIException se) {
					IOException e = new IOException("Error submitting pcode to Git");
					e.initCause(se);
					throw e; 				
				}
			}

			public void processSQL(SQLobject sql) throws IOException 
			{
				String path = basePath + mapper.getPathForSQL(sql, "sql");
				try {
					addFile(sql.getLastChangedBy(),
							path, 
						"Saved at " + ProjectReader.df2.format(sql.getLastChangedDtTm()) + " by " + sql.getLastChangedBy(), 
						sql.getSql().getBytes());				
				} catch (UnmergedPathsException se) {
					IOException e = new IOException("Error submitting SQL to Git");
					e.initCause(se);
					throw e; 				
				} catch (GitAPIException se) {
					IOException e = new IOException("Error submitting SQL to Git");
					e.initCause(se);
					throw e; 				
				}
			}

			@Override
			public void aboutToProcess() {
				System.out.println("Submitting to Git, base path = " + basePath);			
			}
		}
}
