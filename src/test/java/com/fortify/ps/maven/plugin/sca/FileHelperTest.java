package com.fortify.ps.maven.plugin.sca;

import java.io.*;
import java.util.*;
import java.util.zip.*;

import org.junit.Test;

public class FileHelperTest {
    @Test
    public void testFileCopy() throws IOException {
        File src = FileHelper.makeFreshFile("target", "test", "file-copy", "src.txt");
        assert src.exists() : "Failed to create source file, " + src.getAbsolutePath() + ".";
        File dest = FileHelper.nonExistentFile("target", "test", "file-copy", "dest.txt");
        assert !dest.exists() : "Destination file already exists and could not be deleted.";
        boolean success = FileHelper.copyFile(src, dest);
        assert success : "Copying did not succeed perfectly.";
        assert dest.exists() : "Copying did not succeed.";
    }

    @Test
    public void testCopyIntoDirectory() throws IOException {
        File src = FileHelper.makeFreshFile("target", "test", "copy-into-dir", "src.txt");
        File destFolder = FileHelper.getOrCreateDirectory("target", "test", "copy-into-dir", "destination");
        assert destFolder.isDirectory() : "Destination folder could not be created or already exists as a file.";
        File dest = new File(destFolder, src.getName());

        FileHelper.copyFile(src, destFolder);
        assert dest.exists() : "Copy into directory failed.";
    }

    @Test
    public void testDirectoryCopy() throws IOException {
        File srcFolder = new File(FileHelper.getPathString("target", "test", "dir-copy", "sources"));
        srcFolder.mkdirs();
        assert srcFolder.isDirectory() : "Could not create source directory.";
        File src1 = FileHelper.makeFreshFile("target", "test", "dir-copy", "sources", "src1.txt");
        FileHelper.appendTextAsAnotherLine(src1, "Fortify ");
        File src2 = FileHelper.makeFreshFile("target", "test", "dir-copy", "sources", "src2.txt");
        FileHelper.appendTextAsAnotherLine(src2, "Software");
        assert src1.exists() && src2.exists() : "Could not create source files.";
        srcFolder = new File(FileHelper.getPathString("target", "test", "dir-copy", "sources"));
        File destFolder = new File(FileHelper.getPathString("target", "test", "dir-copy", "destination"));
        FileHelper.delete(destFolder);
        destFolder.mkdirs();
        assert destFolder.isDirectory() : "Could not create destination folder.";
        File dest = new File(destFolder, srcFolder.getName());
        boolean success = FileHelper.copy(srcFolder, destFolder);
        assert dest.isDirectory() : "Folder was not copied.";
        assert success : "Copying did not work completely.";
        File[] dirContents = dest.listFiles();
        assert dirContents.length == 2 : "Did not find source files inside destination folder.";
        String str = "";
        File destSrc1 = new File(FileHelper.getPathString("target", "test", "dir-copy", "destination", "sources", "src1.txt"));
        str += FileHelper.readFirstLine(destSrc1);
        File destSrc2 = new File(FileHelper.getPathString("target", "test", "dir-copy", "destination", "sources", "src2.txt"));
        str += FileHelper.readFirstLine(destSrc2);
        assert str.equals("Fortify Software") : "File contents were corrupted: " + str;
    }

    @Test
    public void testZipTester() throws IOException {
        File notAZip = FileHelper.makeFreshFile("target", "test", "zip-tester", "notazip.txt");
        assert notAZip.exists() : "Could not create 'notazip.txt'.";
        assert !FileHelper.isZipArchive(notAZip) : "Misidentified a text file as a ZIP archive.";

        File aZipArchive = null;
        ZipOutputStream zos = null;
        PrintWriter writer = null;
        try {
            aZipArchive = FileHelper.makeFreshFile("target", "test", "zip-tester", "isazip.zip");
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(aZipArchive)));
            zos.putNextEntry(new ZipEntry("readme.txt"));
            writer = new PrintWriter(zos);
            writer.println("Hello, world!");
            writer.flush();
        } finally {
            FileHelper.closeQuietly(zos);
        }
        assert FileHelper.isZipArchive(aZipArchive) : "Did not identify a ZIP archive.";
    }

    @Test
    public void testZipExtraction() throws IOException {
        File zip = null;
        PrintWriter writer = null;
        ZipOutputStream zos = null;

        try {
            zip = FileHelper.makeFreshFile("target", "test", "zip-extraction", "isazip.zip");
            zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)));
            writer = new PrintWriter(zos);
            zos.putNextEntry(new ZipEntry("readme.txt"));
            writer.println("Hello, world!");
            writer.flush();
        } finally {
            FileHelper.closeQuietly(zos);
        }

        File destFolder = null;
        assert FileHelper.isZipArchive(zip) : "Could not create a valid ZIP archive.";
        destFolder = FileHelper.getOrCreateDirectory("target", "test", "zip-extraction", "output");
        assert destFolder.isDirectory() : "Could not create folder to extract to.";
        File[] initialContents = destFolder.listFiles();
        for (File f : initialContents) {
            f.delete();
        }
        initialContents = destFolder.listFiles();
        assert initialContents.length == 0 : "Folder is not empty.";
        FileHelper.extractZipArchive(zip, destFolder);
        File[] files = destFolder.listFiles();
        assert files.length > 0 : "Nothing was extracted.";
        String fileContents = FileHelper.readFirstLine(files[0]);
        assert fileContents != null && fileContents.equals("Hello, world!") : "File extraction was not correct.";
    }

    @Test
    public void testDirectoryDelete() throws IOException {
        File dir = FileHelper.getOrCreateDirectory("target", "test", "dir-delete", "foo");
        for (int i = 0; i < 10; i++) {
            File f = new File(dir, "foo_" + i + ".txt");
            f.createNewFile();
        }
        File subdir = new File(dir, "bar");
        subdir.mkdirs();
        for (int i = 0; i < 10; i++) {
            File f = new File(subdir, "bar_" + i + ".txt");
            f.createNewFile();
        }
        File[] topLevelContents = dir.listFiles();
        assert dir.listFiles().length == 11 : "Error while populating top-level test directory..";
        assert subdir.listFiles().length == 10 : "Error while populating sub-directory.";
        FileHelper.delete(dir);
        assert !dir.exists() : "Unable to delete top-level directory.";
    }

    // Source: http://download.oracle.com/javaee/5/tutorial/doc/bnagy.html#bnagz
    public static final String EXAMPLE_JSP_1 = "<%@ page contentType=\"text/html; charset=UTF-8\" %>\n" +
            "<%@ taglib uri=\"http://java.sun.com/jsp/jstl/core\n" +
            "\"\n" +
            "         prefix=\"c\" %>\n" +
            "<%@ taglib uri=\"/functions\" prefix=\"f\" %>\n" +
            "<html>\n" +
            "<head><title>Localized Dates</title></head>\n" +
            "<body bgcolor=\"white\">\n" +
            "<jsp:useBean id=\"locales\" scope=\"application\"\n" +
            "    class=\"mypkg.MyLocales\"/>\n" +
            "\n" +
            "<form name=\"localeForm\" action=\"index.jsp\" method=\"post\">\n" +
            "<c:set var=\"selectedLocaleString\" value=\"${param.locale}\" />\n" +
            "<c:set var=\"selectedFlag\"\n" +
            "     value=\"${!empty selectedLocaleString}\" />\n" +
            "<b>Locale:</b>\n" +
            "<select name=locale>\n" +
            "<c:forEach var=\"localeString\" items=\"${locales.localeNames}\" >\n" +
            "<c:choose>\n" +
            "    <c:when test=\"${selectedFlag}\">\n" +
            "        <c:choose>\n" +
            "            <c:when\n" +
            "                 test=\"${f:equals(selectedLocaleString, localeString)}\" >\n" +
            "                <option selected>${localeString}</option>\n" +
            "            </c:when>\n" +
            "            <c:otherwise>\n" +
            "                <option>${localeString}</option>\n" +
            "            </c:otherwise>\n" +
            "        </c:choose>\n" +
            "    </c:when>\n" +
            "    <c:otherwise>\n" +
            "        <option>${localeString}</option>\n" +
            "    </c:otherwise>\n" +
            "</c:choose>\n" +
            "</c:forEach>\n" +
            "</select>\n" +
            "<input type=\"submit\" name=\"Submit\" value=\"Get Date\">\n" +
            "</form>\n" +
            "\n" +
            "<c:if test=\"${selectedFlag}\" >\n" +
            "    <jsp:setProperty name=\"locales\"\n" +
            "        property=\"selectedLocaleString\"\n" +
            "        value=\"${selectedLocaleString}\" />\n" +
            "    <jsp:useBean id=\"date\" class=\"mypkg.MyDate\"/>\n" +
            "    <jsp:setProperty name=\"date\" property=\"locale\"\n" +
            "        value=\"${locales.selectedLocale}\"/>\n" +
            "    <b>Date: </b>${date.date}</c:if>\n" +
            "</body>\n" +
            "</html>";

    public static final String EXAMPLE_JSP_2 = "<html><body>Fortify Software</body></html>";

    @Test
    public void testZipArchiveExplosion() throws IOException {
        File war = null;
        PrintWriter writer = null;
        ZipOutputStream zos = null;
        try {
            String warPath = FileHelper.getPathString("target", "test", "zip-explode", "mywebapp.war");
            Map<String, String> warEntries = new HashMap<String, String>();
            warEntries.put("foo.jsp", EXAMPLE_JSP_1);
            warEntries.put("bar.jsp", EXAMPLE_JSP_2);
            war = FileHelper.createZipArchive(warPath, warEntries);
            assert FileHelper.isZipArchive(war) : "WAR creation not successful.";
            boolean success = FileHelper.explodeZipArchive(war);
            //assert success;
            assert war.isDirectory() : "Did not explode WAR at all.";
            File[] contents = war.listFiles();
            assert contents.length == 2 : "Did not explode WAR successfully";
        } finally {
            FileHelper.closeQuietly(zos);
        }
    }

    @Test
    public void testWebAppExplosion() throws IOException {
        Map<String, String> entries = new HashMap<String, String>();
        entries.put("foo.jsp", EXAMPLE_JSP_1);
        entries.put("bar.jsp", EXAMPLE_JSP_2);
        File war_1 = FileHelper.createZipArchive(FileHelper.getPathString("target", "test", "webapp-explode", "war1.war"), entries);
        File war_2 = FileHelper.createZipArchive(FileHelper.getPathString("target", "test", "webapp-explode", "war2.war"), entries);
        File ear = FileHelper.createZipArchive(FileHelper.getPathString("target", "test", "webapp-explode", "myenterpriseapp.ear"), war_1, war_2);
        assert FileHelper.isZipArchive(ear) : "Not a ZIP archive.";
        boolean success = FileHelper.explodeWebApp(ear);
        if (ear.isDirectory()) {
            File war = ear.listFiles()[0];
            if (war.isDirectory()) {
                assert war.listFiles().length == 2;
                return;
            }
        }
        assert false;
    }
}