package decodepcode;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

import decodepcode.compares.ExtractPeopleCodeFromCompareReport;

public class CreateProjectDefProcessor extends ContainerProcessor 
{
	private static class ObjTypes
	{
		int[] i = new int[7];
		ObjTypes( int i0, int i1, int i2, int i3)
		{
			i[0] = i1; i[1] = i1; i[2]=i2; i[3]=i3; i[4]=0; i[5]=0; i[6]=0;
		}
		public int[] getObjTypes() { return i; } 
	}
	private static ObjTypes getObjectTypesFromPCodeType( int pcType)
	{
		if (pcType== 8 ) return new ObjTypes(	1 ,   2  ,  12 ,   0  ); 
		if (pcType== 9 ) return new ObjTypes(	3 ,   4  ,  5  ,   12 );
		if (pcType==39 ) return new ObjTypes(	60,   12 ,  0  ,   0  );
		if (pcType==40 ) return new ObjTypes(	60,   87 ,  12 ,   0  );
		if (pcType==43 ) return new ObjTypes(	66,   77 ,  78 ,   12 );
		if (pcType==43 ) return new ObjTypes(	66,   77 ,  39 ,   20 );
		if (pcType==42 ) return new ObjTypes(	74,   12 ,   0 ,    0 );
		if (pcType==44 ) return new ObjTypes(	9 ,   12 ,  0  ,   0  );
		if (pcType==46 ) return new ObjTypes(	10,   39 ,  12 ,   0  );
		if (pcType==47 ) return new ObjTypes(	10,   39 ,  1  ,   12 );
		if (pcType==48 ) return new ObjTypes(	10,   39 ,  1  ,   2  );
		return null;
	}
	public static int[] getObjTypesFromPCType( int pcType)
	{
		ObjTypes o = getObjectTypesFromPCodeType(pcType);
		if (o == null)
			return null;
		return o.getObjTypes();
	}
	static class BarePTobject implements PeopleToolsObject
	{
		int[] objTypes;
		String[] objValues;
		int type;
		public BarePTobject( PeopleCodeObject o ) {
			objTypes = o.getKeyTypes();
			objValues = o.getKeys();
			type = o.getPeopleCodeType();
		}
		public int[] getKeyTypes() {
			return objTypes;
		}

		public String[] getKeys() {
			return objValues;
		}
		public String getLastChangedBy() {
			return null;
		}
		public Date getLastChangedDtTm() {
			return null;
		}
		public int getPeopleCodeType() {
			return type;
		}
		public String getSource() {
			return null;
		}	
		public boolean equals( Object o1)
		{
			BarePTobject o = (BarePTobject) o1;
			return type == o.type 
			&& objValues[0].equals(o.objValues[0])
			&& objValues[1].equals(o.objValues[1])
			&& objValues[2].equals(o.objValues[2])
			&& objValues[3].equals(o.objValues[3]);
		}
	}

	Set<BarePTobject> list;
	File projectToCreate;
	Logger logger = Logger.getLogger(this.getClass().getName());
	SimpleDateFormat df;
	String projName;
	public CreateProjectDefProcessor( File _projectToCreate) 
	{
		projectToCreate= _projectToCreate;
		if (!projectToCreate.getName().endsWith(".xml"))
			throw new IllegalArgumentException("Expected .xml file name, got: " + projectToCreate);
		projName = projectToCreate.getName().substring(0, projectToCreate.getName().length() - 4).toUpperCase();
		df = new SimpleDateFormat("yyyy-MM-dd-HH.mm.ss.000000");
	}
	@Override
	public void aboutToProcess() 
	{
		list = new HashSet<BarePTobject>();
	}
	@Override
	public void finishedProcessing() 
	{
		try 
		{
		PrintWriter p = new PrintWriter(projectToCreate);

		p.println("<?xml version='1.0'?>");
		p.println("  <!--Warning : Don't edit this file -->");
		p.println("  <instance class=\"PJM\">");
		p.println("    <rowset name=\"PjmDefn\" size=\"1628\" count=\"1\">");
		p.println("      <row>");
		p.println("        <szLastUpdDttm>" + df.format(new Date()) + "</szLastUpdDttm>");
		p.println("        <szLastUpdOprId>" + System.getProperty("user.name").substring(0,6)+ "</szLastUpdOprId>");
		p.println("        <szObjectOwnerID></szObjectOwnerID>");
		p.println("        <nUseCount>1</nUseCount>");
		p.println("        <bUseUpdate>1</bUseUpdate>");
		p.println("        <lVersion>231</lVersion>");
		p.println("        <lSwapVersion>0</lSwapVersion>");
		p.println("        <nSwapNum>0</nSwapNum>");
		p.println("        <szProjectName>" + projName + "</szProjectName>");
		p.println("        <szLanguageCd>ENG</szLanguageCd>");
		p.println("        <szProjectDescr></szProjectDescr>");
		p.println("        <szTgtServerName></szTgtServerName>");
		p.println("        <szTgtDBName></szTgtDBName>");
		p.println("        <szTgtOprId></szTgtOprId>");
		p.println("        <fReportFilter>16232832</fReportFilter>");
		p.println("        <eTargetOrientation>0</eTargetOrientation>");
		p.println("        <eCompareType>1</eCompareType>");
		p.println("        <fKeepTgt>31</fKeepTgt>");
		p.println("        <nCommitLimit>50</nCommitLimit>");
		p.println("        <szCompRelease></szCompRelease>");
		p.println("        <szCompRelDttm></szCompRelDttm>");
		p.println("        <bMaintenanceProject>0</bMaintenanceProject>");
		p.println("        <bUseDbFieldNameAttrib>0</bUseDbFieldNameAttrib>");
		p.println("        <szReleaseLabel></szReleaseLabel>");
		p.println("        <szReleaseDttm></szReleaseDttm>");
		p.println("        <nDescrLongLen>0</nDescrLongLen>");
		p.println("        <hDescrLong>HANDLE</hDescrLong>");
		p.println("        <nProjectCount>1</nProjectCount>");
		p.println("        <lpPit> POINTER");
		p.println("          <rowset name=\"PjmPit\" size=\"" + (list.size() * 296) + "\" count=\"" + list.size() + "\">");
		for (BarePTobject o: list)			
		{
		p.println("            <row>");
		p.println("              <eObjectType>" + o.getPeopleCodeType() + "</eObjectType>");
		p.println("              <szObjectValue_0>" + o.getKeys()[0] + "</szObjectValue_0>");
		p.println("              <szObjectValue_1>" + (o.getKeys()[1] == null? "" : o.getKeys()[1]) + "</szObjectValue_1>");
		p.println("              <szObjectValue_2>" + (o.getKeys()[2] == null? "" : o.getKeys()[2]) + "</szObjectValue_2>");
		p.println("              <szObjectValue_3>" + (o.getKeys()[3] == null? "" : o.getKeys()[3]) + "</szObjectValue_3>");
		p.println("              <eObjectID_0>" + o.getKeyTypes()[0] + "</eObjectID_0>");
		p.println("              <eObjectID_1>" + o.getKeyTypes()[1] + "</eObjectID_1>");
		p.println("              <eObjectID_2>" + o.getKeyTypes()[2] + "</eObjectID_2>");
		p.println("              <eObjectID_3>" + o.getKeyTypes()[3] + "</eObjectID_3>");
		p.println("              <bExecute>0</bExecute>");
		p.println("              <eSourceStatus>0</eSourceStatus>");
		p.println("              <eTargetStatus>0</eTargetStatus>");
		p.println("              <eUpgradeAction>0</eUpgradeAction>");
		p.println("              <bTakeAction>1</bTakeAction>");
		p.println("              <bCopyDone>0</bCopyDone>");
		p.println("              <eRowStatus>3</eRowStatus>");
		p.println("            </row>");
		}
		p.println("          </rowset>");
		p.println("        </lpPit>");
		p.println("        <bCopyAllSec>1</bCopyAllSec>");
		p.println("        <bCompAllSec>1</bCompAllSec>");
		p.println("        <nSecCount>54</nSecCount>");
		p.println("        <lpSec> POINTER");
		p.println("          <rowset name=\"PjmSec\" size=\"1080\" count=\"54\">");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>COMMON</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>ARA</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>CFR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>CZE</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>DAN</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>DUT</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>ENG</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>ESP</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>FIN</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>FRA</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>GER</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>GRK</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>HEB</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>HUN</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>ITA</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>JPN</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>KOR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>MAY</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>NOR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>POL</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>POR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>RUS</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>SVE</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>THA</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>TUR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>ZHS</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>0</eProcessType>");
		p.println("              <szSectionName>ZHT</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>COMMON</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>ARA</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>CFR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>CZE</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>DAN</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>DUT</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>ENG</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>ESP</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>FIN</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>FRA</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>GER</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>GRK</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>HEB</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>HUN</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>ITA</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>JPN</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>KOR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>MAY</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>NOR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>POL</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>POR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>RUS</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>SVE</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>THA</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>TUR</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>ZHS</szSectionName>");
		p.println("            </row>");
		p.println("            <row>");
		p.println("              <eProcessType>2</eProcessType>");
		p.println("              <szSectionName>ZHT</szSectionName>");
		p.println("            </row>");
		p.println("          </rowset>");
		p.println("        </lpSec>");
		p.println("        <nIncCount>0</nIncCount>");
		p.println("        <lpInc>POINTER</lpInc>");
		p.println("        <nDepCount>0</nDepCount>");
		p.println("        <lpDep>POINTER</lpDep>");
		p.println("        <nLangCount>0</nLangCount>");
		p.println("        <hLang>HANDLE</hLang>");
		p.println("      </row>");
		p.println("    </rowset>");
		p.println("  </instance>");
		p.println("");		
		p.println("");		
		p.close();
		logger.info("Created " + projectToCreate);
		}
		catch (Exception ex) { 
			ex.printStackTrace();
			logger.severe(ex.getMessage());
		}
	} 
	@Override
	public void process(PeopleCodeObject c) throws IOException {
		BarePTobject c1 = new BarePTobject(c);
		if (list.contains(c1))
			logger.severe("Duplicate object: " + c);
		if (c1.getKeyTypes() != null)
			list.add(c1);
	}

	@Override
	public void processSQL(SQLobject sql) throws IOException {
		// TODO Auto-generated method stub
		
	}
	
	public static void main( String[] a)
	{
		try {
			ContainerProcessor processor = new CreateProjectDefProcessor( new File("c:\\temp\\testproject.xml"));
				//new Controller.WriteDecodedPPCtoDirectoryTree(new File("c:\\temp\\decodePcode\\extract"));
			new ExtractPeopleCodeFromCompareReport().
				processPeopleCodeFromTree(new File("C:\\projects\\sandbox\\PeopleCode\\compare_reports\\UPGCUST"),
						processor, true, false);
			
/*			processor.aboutToProcess();
			ProjectReader p = new ProjectReader();
			p.setProcessor(processor);
			p.readProject( new File("C:\\projects\\sandbox\\PeopleCode\\project_to_file\\EVH_TEST2\\EVH_TEST2.xml"));
			processor.finishedProcessing();
			*/
		} catch (Exception e) {
			e.printStackTrace();
			// TODO: handle exception
		}
	}
}
