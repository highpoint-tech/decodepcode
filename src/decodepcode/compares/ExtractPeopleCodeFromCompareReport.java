package decodepcode.compares;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.logging.Logger;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import decodepcode.ContainerProcessor;
import decodepcode.Controller;
import decodepcode.CreateProjectDefProcessor;
import decodepcode.PToolsObjectToFileMapper;
import decodepcode.PeopleCodeObject;
import decodepcode.ProjectReader;



public class ExtractPeopleCodeFromCompareReport 
{
	static Logger logger = Logger.getLogger(ExtractPeopleCodeFromCompareReport.class.getName());
	final static String eol = System.getProperty("line.separator"),
		DEMOTREE   = "PeopleCodeTrees/oldDEMO/",
		SOURCETREE = "PeopleCodeTrees/source/",
		TARGETTREE = "PeopleCodeTrees/target/";
	
	private static HashMap<String,String> htmlEntities;
	  static {
		    htmlEntities = new HashMap<String,String>();
		    htmlEntities.put("&lt;","<")    ; htmlEntities.put("&gt;",">");
		    htmlEntities.put("&amp;","&")   ; htmlEntities.put("&quot;","\"");
		    htmlEntities.put("&agrave;","à"); htmlEntities.put("&Agrave;","À");
		    htmlEntities.put("&acirc;","â") ; htmlEntities.put("&auml;","ä");
		    htmlEntities.put("&Auml;","Ä")  ; htmlEntities.put("&Acirc;","Â");
		    htmlEntities.put("&aring;","å") ; htmlEntities.put("&Aring;","Å");
		    htmlEntities.put("&aelig;","æ") ; htmlEntities.put("&AElig;","Æ" );
		    htmlEntities.put("&ccedil;","ç"); htmlEntities.put("&Ccedil;","Ç");
		    htmlEntities.put("&eacute;","é"); htmlEntities.put("&Eacute;","É" );
		    htmlEntities.put("&egrave;","è"); htmlEntities.put("&Egrave;","È");
		    htmlEntities.put("&ecirc;","ê") ; htmlEntities.put("&Ecirc;","Ê");
		    htmlEntities.put("&euml;","ë")  ; htmlEntities.put("&Euml;","Ë");
		    htmlEntities.put("&iuml;","ï")  ; htmlEntities.put("&Iuml;","Ï");
		    htmlEntities.put("&ocirc;","ô") ; htmlEntities.put("&Ocirc;","Ô");
		    htmlEntities.put("&ouml;","ö")  ; htmlEntities.put("&Ouml;","Ö");
		    htmlEntities.put("&oslash;","ø") ; htmlEntities.put("&Oslash;","Ø");
		    htmlEntities.put("&szlig;","ß") ; htmlEntities.put("&ugrave;","ù");
		    htmlEntities.put("&Ugrave;","Ù"); htmlEntities.put("&ucirc;","û");
		    htmlEntities.put("&Ucirc;","Û") ; htmlEntities.put("&uuml;","ü");
		    htmlEntities.put("&Uuml;","Ü")  ; htmlEntities.put("&nbsp;"," ");
		    htmlEntities.put("&copy;","\u00a9");
		    htmlEntities.put("&reg;","\u00ae");
		    htmlEntities.put("&euro;","\u20a0");
		  }
	  public static final String unescapeHTML(String source) {
	      int i, j;

	      boolean continueLoop;
	      int skip = 0;
	      do {
	         continueLoop = false;
	         i = source.indexOf("&", skip);
	         if (i > -1) {
	           j = source.indexOf(";", i);
	           if (j > i) {
	             String entityToLookFor = source.substring(i, j + 1);
	             String value = (String) htmlEntities.get(entityToLookFor);
	             if (value != null) {
	               source = source.substring(0, i)
	                        + value + source.substring(j + 1);
	               continueLoop = true;
	             }
	             else if (value == null){
	                skip = i+1;
	                continueLoop = true;
	             }
	           }
	         }
	      } while (continueLoop);
	      return source;
	  }

	public class PeopleCodeSegment implements PeopleCodeObject 
	{
		ArrayList<String> keys = new ArrayList<String>();
		StringWriter peopleCode= new StringWriter();;

		public String getCompositeKey()
		{
			StringWriter w = new StringWriter();
			for (String s: keys)
				w.write(s + " ");
			return w.toString().trim();
		}
		void addKey( String k)
		{ 
			keys.add(k);
		}
		void addPeoplCodeLine(String l)
		{
			if (l != null && l.trim().length() > 0)
				peopleCode.write(l);
			peopleCode.write(eol);
		}
		public String toString()
		{
			return "PeopleCode for " + getCompositeKey() + ": " + eol + peopleCode; 
		}
		public String[] getKeys() {
			return (String[]) keys.toArray(new String[7]);
		}
		public String getLastChangedBy() {
			return "Not available";
		}
		public Date getLastChangedDtTm() {
			return null;
		}
		public int getPeopleCodeType() {
			return peopleCodeType;
		}
		public String getSource() {
			return xml.getName();
		}
		public String getPeopleCodeText() {
			return peopleCode.toString();
		}
		public boolean hasPlainPeopleCode() {
			return true;
		}
		public int[] getKeyTypes() {
			return CreateProjectDefProcessor.getObjTypesFromPCType(peopleCodeType);
		}
	}

	ArrayList< PeopleCodeSegment> list;
	ContainerProcessor processor;
	PeopleCodeSegment segment;
	File xml;
	int peopleCodeType;
	boolean extractTargetPPC;
	DocumentBuilder docBuilder;
	Document d;
	boolean addLinksToXML = false;
	
	public ExtractPeopleCodeFromCompareReport() throws ParserConfigurationException
	{
		docBuilder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
	}
	
	
	private void visit(Node node, int level)
	{
//		logger.info("Level = " + level + ", node = " + node.getNodeName());
		NodeList nl = node.getChildNodes();		
		Node firstAttribute = null;
		for(int i=0, cnt=nl.getLength(); i<cnt; i++)
		{
			boolean newSegment = false;
			Node n = nl.item(i);
			if ((level == 2)  && (n.getNodeType() == Node.ELEMENT_NODE) && "item".equals(((Element) n).getNodeName()))
			{
				segment = new PeopleCodeSegment();
				list.add(segment);
				newSegment = true;
				firstAttribute = null;
			}
			else
				if (level == 3 && n.getNodeType() == Node.ELEMENT_NODE && "objname".equals(((Element) n).getNodeName()))
					segment.addKey(n.getTextContent());
				else
					if (level == 4 && n.getNodeType() == Node.ELEMENT_NODE && "attribute".equals(((Element) n).getNodeName()))
					{
						if (firstAttribute == null)	
							firstAttribute = n;
					}
					else
					if (!extractTargetPPC && level == 5 && n.getNodeType() == Node.ELEMENT_NODE && "source".equals(((Element) n).getNodeName()))
						segment.addPeoplCodeLine(unescapeHTML(n.getTextContent()));
					else
						if (extractTargetPPC && level == 5 && n.getNodeType() == Node.ELEMENT_NODE && "target".equals(((Element) n).getNodeName()))
							segment.addPeoplCodeLine(unescapeHTML(n.getTextContent()));
			//  <attribute diff="targetonly" name="">

			if (level != 4 || (n.getNodeType() != Node.ELEMENT_NODE) 
					|| !("attribute").equals(((Element) n).getNodeName())
					|| "same".equals(((Element) n).getAttribute("diff"))
					|| ("sourceonly".equals(((Element) n).getAttribute("diff")) && !extractTargetPPC)
					|| ("targetonly".equals(((Element) n).getAttribute("diff")) && extractTargetPPC)
			)
				visit(n, level + 1);
			if (newSegment && addLinksToXML)
				{
					try {
						File topDir = new File(new File(xml.getParent()).getParent());
						String topDirSource = new File(topDir, SOURCETREE).toURL().toString(),
							   topDirTarget = new File(topDir, TARGETTREE).toURL().toString();
						if (processor instanceof Controller.WriteDecodedPPCtoDirectoryTree)
						{
							PToolsObjectToFileMapper mapper = ((Controller.WriteDecodedPPCtoDirectoryTree) processor).getMapper();
							File outFile = mapper.getFile(segment, "pcode");
							URL url = outFile.toURL();	
							logger.info("Output file: url is " + url);
							Element n2 = d.createElement("attribute");
							n2.setAttribute("diff", "same");
							n2.setAttribute("name", "pcode");
							NodeList nl2 = n.getChildNodes();		
							for(int i2=0, cnt2=nl2.getLength(); i2<cnt2; i2++)
							{
								Node n0 = nl2.item(i2);
								if (n0.getNodeType() == Node.ELEMENT_NODE && n0.getNodeName() == "secondary_key")
								{
									NodeList nl3 = n0.getChildNodes();
									if (nl3.getLength() > 0)
										n0.insertBefore(n2, nl3.item(0));
								}
							}
							
							if (url.toString().startsWith(topDirSource))
							{
								String relURL = "./"+  SOURCETREE+ url.toString().substring(topDirSource.length());
								Element n3 = d.createElement("source");
								n3.setTextContent(relURL);
								n2.appendChild(n3);
								//((Element) n).setAttribute("sourcepcode", relURL);
								//logger.info("Relative source url = "+ relURL);
							}
							if (url.toString().startsWith(topDirTarget))
							{
								String relURL = "./" + TARGETTREE + url.toString().substring(topDirTarget.length());
								((Element) n).setAttribute("targetpcode", relURL);
								Element n3 = d.createElement("target");
								n3.setTextContent(relURL);
								n2.appendChild(n3);
								logger.info("Relative target url = "+ relURL);
							}
						}
						
					} catch (MalformedURLException e) {
						logger.severe("??? "+ e);
					}
					catch (IOException e) {
						logger.severe("??? "+ e);
					}
					
				}
				
		}
	}
	
	static int nameToType( String name)
	{
		if (name.startsWith("Application Engine PeopleCode"))
			return 43;
		if (name.startsWith("Application Package PeopleCode"))
			return 58;		
		if (name.startsWith("Component PeopleCode"))
			return 46;
		if (name.startsWith("Comp. Interface PeopleCode"))
			return 42;		
		if (name.startsWith("Component Rec Fld PeopleCode"))
			return 48;
		if (name.startsWith("Component Record PeopleCode"))
			return 47;		
		if (name.startsWith("Page PeopleCode"))
			return 44;		
		if (name.startsWith("Record PeopleCode"))
			return 8;		
		if (name.startsWith("Subscription PeopleCode"))
			return 40;		
		return -1;
	}
	
	public void processPeopleCodeFromFile( File _xml, ContainerProcessor _processor, boolean _extractTargetPPC) 
			throws SAXException, IOException, ParserConfigurationException, TransformerException
	{
		xml = _xml;
		processor = _processor;
		list = new ArrayList<PeopleCodeSegment>();
		peopleCodeType = nameToType(xml.getName());
		if (peopleCodeType < 0)
			return;
		extractTargetPPC = _extractTargetPPC;
		logger.info("Now extracting " + (extractTargetPPC? "target" : "source" ) + " PeopleCode from " + xml);
		d = docBuilder.parse(xml);
		visit(d, 0);
		for (PeopleCodeSegment segment: list)
			processor.process(segment);
		
		if (addLinksToXML)
		{
			TransformerFactory tf = TransformerFactory.newInstance();
			Transformer t = tf.newTransformer();
			DOMSource s = new DOMSource(d);
//			File xml2 = new File(xml.getParent(), xml.getName().substring(0, xml.getName().length()-4)+ "b.xml");
			StreamResult sr1 = new StreamResult(xml);  
			t.transform(s,sr1);				
/*			if (!xml.delete())
				logger.severe("Could not delete "+ xml + " after creating modified copy");
			else
				if (!xml2.renameTo(xml))
					logger.severe("Could not rename "+ xml2 + " to " + xml);
					*/
		}
		
	}
	
	public void processPeopleCodeFromTree( File compareDir, ContainerProcessor processor, 
			boolean _extractTargetPPC, boolean _addLinksToXML) 
		throws SAXException, IOException, ParserConfigurationException, TransformerException
	{
		addLinksToXML = _addLinksToXML;
		processor.aboutToProcess();
		if (!(compareDir.exists() && compareDir.isDirectory()))
			throw new IllegalArgumentException("Expected top directory of compare reports; got "  + compareDir);
		for (String folder: compareDir.list(new FilenameFilter() {			
			public boolean accept(File dir, String name) {				
				return new File(dir, name).isDirectory() && name.contains("PeopleCode");
			}
		}))
		{
			File folderDir = new File(compareDir, folder);
			logger.info("Going into directory "+ folderDir.getAbsolutePath());
			for (String xmlFileName: folderDir.list(new FilenameFilter() {				
				public boolean accept(File dir, String name) {
					return name.contains("PeopleCode") && name.endsWith(".xml");
				}
			}))
				processPeopleCodeFromFile(new File(folderDir, xmlFileName), processor, _extractTargetPPC);
		}
		processor.finishedProcessing();
	}
	

	
	/**
	 * @param args
	 */
	public static void main(String[] args) {
		logger.info("Start");
		try {
			/*
			File file = new File("C:\\projects\\JPMC\\Compare_Reports\\UPGCUST\\Component PeopleCode\\Component PeopleCode_1.xml");
			new ExtractPeopleCodeFromCompareReport().processPeopleCodeFromFile(file, 
					new Controller.WriteDecodedPPCtoDirectoryTree(new File("c:\\temp\\decodePcode\\extract")), false);
			*/
			File compareDir = new File("C:\\projects\\sandbox\\PeopleCode\\compare_reports\\UPGCUST");
			/*
			new ExtractPeopleCodeFromCompareReport().
				processPeopleCodeFromTree(compareDir),
					new Controller.WriteDecodedPPCtoDirectoryTree(new File("c:\\temp\\decodePcode\\extract")), true);
					*/
			File oldDemoXML = new File("C:\\projects\\sandbox\\PeopleCode\\project_to_file\\FromVLQTAJ\\testproject.xml");
			ProjectReader p = new ProjectReader();
			p.setProcessor(new Controller.WriteDecodedPPCtoDirectoryTree(new File(compareDir, DEMOTREE)));
			p.readProject(oldDemoXML);
			
			new ExtractPeopleCodeFromCompareReport().
				processPeopleCodeFromTree(compareDir,
						new Controller.WriteDecodedPPCtoDirectoryTree(new File(compareDir, TARGETTREE)), true, false);

			new ExtractPeopleCodeFromCompareReport().
				processPeopleCodeFromTree(compareDir,
						new Controller.WriteDecodedPPCtoDirectoryTree(new File(compareDir, SOURCETREE)), false, false);
			

					
		} catch (Exception e) {
			e.printStackTrace();
		} 
		logger.info("Ready");
	}

}
