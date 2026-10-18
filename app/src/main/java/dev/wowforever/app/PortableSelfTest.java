package dev.wowforever.app;

import dev.wowforever.importer.fs.SourcePaths;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.DriverManager;
import java.util.*;

/** Offline native-package smoke only: no catalogue, user preferences, network or graphical startup. */
public final class PortableSelfTest {
  public static final String SUCCESS_MARKER="VIBECRAFT_PORTABLE_SELF_TEST_OK";
  public static final String FAILURE_MARKER="VIBECRAFT_PORTABLE_SELF_TEST_FAILED";
  private static final int MAX_REPORT_BYTES=8192;
  @FunctionalInterface interface Probe { String sqliteVersion(Path nativeDirectory)throws Exception; }
  private PortableSelfTest() {}

  /** Exit 0 means the bundled runtime/JNI prerequisite smoke passed, not GUI or gameplay-data acceptance. */
  public static int run(Path requestedReport) {return run(requestedReport,PortableSelfTest::probe);}

  static int run(Path requestedReport,Probe probe) {
    Path report;
    try {report=validateReport(requestedReport);}
    catch(IOException|RuntimeException failure){diagnostic(failure);return 2;}
    boolean success=false;String sqliteVersion="",error="";
    try {
      if(Runtime.version().feature()<21)throw new IOException("Java 21 or newer is required");
      Path nativeDirectory=report.getParent().resolve("sqlite-native");
      Files.createDirectory(nativeDirectory);
      System.setProperty("org.sqlite.tmpdir",nativeDirectory.toString());
      sqliteVersion=probe.sqliteVersion(nativeDirectory);
      if(sqliteVersion==null||!sqliteVersion.matches("[0-9]+(?:\\.[0-9]+){1,3}"))throw new IOException("SQLite native version is invalid");
      success=true;
    }catch(Exception|LinkageError failure){error=bounded(failure.getClass().getSimpleName()+": "+Objects.toString(failure.getMessage(),"Prerequisite check failed"),400);}
    sqliteVersion=bounded(Objects.toString(sqliteVersion,""),128);
    String productVersion=System.getProperty("wowforever.release.version",Objects.toString(PortableSelfTest.class.getPackage().getImplementationVersion(),"development"));
    String json="{\n  \"formatVersion\": 1,\n  \"success\": "+success+",\n  \"marker\": "+quote(success?SUCCESS_MARKER:FAILURE_MARKER)
        +",\n  \"productVersion\": "+quote(bounded(productVersion,128))
        +",\n  \"javaFeature\": "+Runtime.version().feature()
        +",\n  \"osName\": "+quote(bounded(System.getProperty("os.name","unknown"),128))
        +",\n  \"osArch\": "+quote(bounded(System.getProperty("os.arch","unknown"),128))
        +",\n  \"javaHome\": "+quote(bounded(System.getProperty("java.home","unknown"),1024))
        +",\n  \"sqliteNativeVersion\": "+quote(sqliteVersion)
        +",\n  \"scope\": \"offline native prerequisites; GUI and gameplay data are not checked\""
        +(success?"":",\n  \"error\": "+quote(error))+"\n}\n";
    try {
      byte[] bytes=json.getBytes(StandardCharsets.UTF_8);
      if(bytes.length>MAX_REPORT_BYTES)throw new IOException("Portable smoke report exceeds its bound");
      SourcePaths.rejectSymlinks(report);
      if(!report.getParent().toRealPath().equals(report.getParent()))throw new IOException("Portable smoke storage changed");
      try(var output=Files.newOutputStream(report,StandardOpenOption.CREATE_NEW,StandardOpenOption.WRITE)){output.write(bytes);}
    }catch(IOException|RuntimeException failure){diagnostic(failure);return 2;}
    if(!success)System.err.println(FAILURE_MARKER+": "+error);
    return success?0:1;
  }

  private static Path validateReport(Path requested)throws IOException {
    if(requested==null||!requested.isAbsolute())throw new IOException("Portable smoke report must be an absolute path");
    SourcePaths.rejectSymlinks(requested);
    Path normalized=requested.toAbsolutePath().normalize(),parent=normalized.getParent();
    if(parent==null||parent.getFileName()==null||!parent.getFileName().toString().startsWith("vibecraft-portable-self-test-")
        ||!Files.isDirectory(parent,LinkOption.NOFOLLOW_LINKS))throw new IOException("Choose existing dedicated Vibecraft portable self-test storage");
    for(Path ancestor=parent;ancestor!=null;ancestor=ancestor.getParent())
      if(Files.exists(ancestor.resolve(".build.info"),LinkOption.NOFOLLOW_LINKS))throw new IOException("WoW installation directories are read-only");
    if(Files.exists(normalized,LinkOption.NOFOLLOW_LINKS))throw new IOException("Portable smoke report already exists");
    try(var entries=Files.list(parent)){if(entries.findAny().isPresent())throw new IOException("Portable self-test storage must be empty");}
    return parent.toRealPath().resolve(normalized.getFileName());
  }

  private static String probe(Path nativeDirectory)throws Exception {
    ClassLoader loader=PortableSelfTest.class.getClassLoader();
    for(String name:List.of("javax.swing.JPanel","java.net.http.HttpClient","java.util.prefs.Preferences",
        "dev.wowforever.importer.setup.LibraryPreparationWorkflow"))Class.forName(name,false,loader);
    if(!nativeDirectory.toString().equals(System.getProperty("org.sqlite.tmpdir")))throw new IOException("SQLite extraction storage was not configured");
    Class.forName("org.sqlite.JDBC",true,loader);
    try(var connection=DriverManager.getConnection("jdbc:sqlite::memory:");var statement=connection.createStatement();
        var result=statement.executeQuery("SELECT 1, sqlite_version()")) {
      if(!result.next()||result.getInt(1)!=1)throw new IOException("SQLite native in-memory query failed");
      return result.getString(2);
    }
  }
  private static void diagnostic(Throwable failure){System.err.println(FAILURE_MARKER+": "+bounded(Objects.toString(failure.getMessage(),"Unsafe portable smoke storage"),400));}
  private static String bounded(String value,int limit){return value.length()>limit?value.substring(0,limit):value;}
  private static String quote(String value) {
    StringBuilder result=new StringBuilder("\"");
    for(int i=0;i<value.length();i++) {
      char ch=value.charAt(i);
      switch(ch){case '"'->result.append("\\\"");case '\\'->result.append("\\\\");case '\n'->result.append("\\n");case '\r'->result.append("\\r");case '\t'->result.append("\\t");
        default->{if(ch<32)result.append(String.format(Locale.ROOT,"\\u%04x",(int)ch));else result.append(ch);}}
    }
    return result.append('"').toString();
  }
}
