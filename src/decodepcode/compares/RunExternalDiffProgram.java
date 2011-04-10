package decodepcode.compares;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.io.StringWriter;

public class RunExternalDiffProgram 
{
	public final static String  eol = System.getProperty("line.separator");
	
	public static String getLongDiff( File f1, File f2) throws IOException
	{
		return getDiff("C:\\progs\\sundries\\diff.exe", "-y", f1, f2);
	}
	
	public static String getShortDiff( File f1, File f2) throws IOException
	{
		long count = 0;
		BufferedReader br = new BufferedReader(new StringReader( getLongDiff(f1, f2)));
		while (br.readLine() != null)
			count++;
		return "" + count + " line(s) in diff";
	}

	
	public static String getDiff( String diffProg, String params, File f1, File f2) throws IOException
	{
		if (!f1.exists() )
			throw new IllegalArgumentException("File "+ f1 + " does not exist");
		if (!f2.exists() )
			throw new IllegalArgumentException("File "+ f2 + " does not exist");
		
		String[] cmdArray  = { diffProg, params, f1.toString(), f2.toString()};
		String cmd = "";
		for (String s: cmdArray) 
			cmd += s + " ";
//		System.out.println(cmd);
		Process p = Runtime.getRuntime().exec(cmdArray);
		InputStream stdout = p.getInputStream(), 
					stderr = p.getErrorStream();
		BufferedReader br = new BufferedReader(new InputStreamReader(stdout)),
				brErr  = new BufferedReader(new InputStreamReader(stderr));
		String line = "", line2 = null;
		StringWriter w = new StringWriter();

		while ( (line = br.readLine()) != null || ((line2 = brErr.readLine()) != null))
		{
			if (line != null) 
			{
				w.write(line);
				w.write(eol);
			}
			if (line2 != null) w.write("stderr> " + line2 + eol);
		}
		//System.out.println("Exit value = " + p.exitValue());
		return w.toString();
	}
	
	public RunExternalDiffProgram() throws IOException, InterruptedException
	{
		String diffProg = "C:\\progs\\sundries\\diff.exe",
			params = "",
			path = "/Component_PeopleCode/ASSIGNMENT_DATA/GBL/PreBuild.pcode";
		File dir = new File("C:\\projects\\big\\Compare_Reports\\UPGCUST\\PeopleCodeTrees\\"),
			  f1 = new File(dir, "PSNEW89/" + path),
			  f2 = new File(dir, "PSVAN91/" + path);
		System.out.println(getDiff(diffProg, params, f1, f2));
	}
	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			new RunExternalDiffProgram();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

}
