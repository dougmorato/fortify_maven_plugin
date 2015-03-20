package com.fortify.ps.maven.plugin.sca;

import java.io.*;
import java.util.*;
import java.util.zip.*;

public class FileHelper {
	/**
	 * The destination can be a directory or file. If the former, then the file will be copied into the directory.
	 *
	 * @param src   the source file, not a directory, to copy
	 * @param dest  the file to copy to or the directory to copy into
	 * @return      whether or not the copy was performed. A false value indicates that a precondition was not met.
	 * @throws      IOException
	 */
	public static boolean copyFile(File src, File dest, boolean overwrite) throws IOException {
		if (src == null || dest == null || !src.exists() || (!overwrite && dest.exists() && !dest.isDirectory()) || src.isDirectory()) {
			 return false;
		}

		if (dest.isDirectory()) {
		File newDest = new File(dest, src.getName());
			if (newDest.exists()) {
				return false;
			}
			dest = newDest;
		}

		BufferedInputStream srcStream = new BufferedInputStream(new FileInputStream(src));
		BufferedOutputStream destStream = new BufferedOutputStream(new FileOutputStream(dest));

		try {
			byte[] buf = new byte[8192];
			int numBytes;
			while ((numBytes = srcStream.read(buf, 0, buf.length)) != -1) {
				destStream.write(buf, 0, numBytes);
			}
			return true;
		} finally {
			if (srcStream != null) srcStream.close();
			if (destStream != null) destStream.close();
		}
	}

	public static boolean copyFile(File src, File dest) throws IOException {
		boolean overwrite = false;
		return copyFile(src, dest, overwrite);
	}

	public static boolean copy(File src, File dest, boolean overwrite) throws IOException {
		if (src != null && dest != null) {
			if (src.exists()) {
				if (src.isDirectory()) {
					File dir = null;

					if (dest.isDirectory()) {
						dir = new File(dest, src.getName());
						dir.mkdir();
					} else if (!dest.exists()) {
						dest.mkdirs();
						dir = dest;
					} else {
						// If the destination is not a directory, then it should not already exist. The caller should
						// have deleted it prior to calling this method.
					    return false;
					}

					if (dir != null && dir.isDirectory()) {
						File[] contents = src.listFiles();

						// TODO: Report partial success.
						for (File f : contents) {
							copy(f, dir, overwrite);
						}
						return true;
					}
				} else {
					return copyFile(src, dest, overwrite);
				}
			}
		}
		return false;
	}

	public static boolean copy(File src, File dest) throws IOException {
		boolean overwrite = false;
		return copy(src, dest, overwrite);
	}

	public static boolean isZipArchive(File zipPath) {
		// Attempting to close a ZIP archive that is not really a ZIP archive results in an IOException.
		if (zipPath != null && zipPath.exists()) {
			try {
				ZipFile zip = new ZipFile(zipPath);
				zip.close();
				return true;
			} catch (IOException ioe) {
				return false;
			}
		}
		return false;
	}

	/**
	 * Extracts the contents of the ZIP archive to the directory specified by dest.
	 *
	 * @param zipPath   the location of the ZIP archive
	 * @param dest      the directory to copy the archive's contents into
	 * @return          whether or not the contents of the ZIP archive were totally extracted
	 */
	public static boolean extractZipArchive(File zipPath, File dest) throws IOException {
		// TODO: Handle the case in which entries are actually directories.
		if (isZipArchive(zipPath) && dest != null && dest.isDirectory()) {
			ZipFile zip = null;

			try {
				zip = new ZipFile(zipPath);
				Enumeration<? extends ZipEntry> iter = zip.entries();
				while (iter.hasMoreElements()) {
					ZipEntry curr = iter.nextElement();
					BufferedInputStream in = null;
					BufferedOutputStream out = null;

					try {
						if (curr.isDirectory()) {
							File dir = new File(dest, curr.getName());
							dir.mkdirs();
						} else {
							in = new BufferedInputStream(zip.getInputStream(curr));
							out = new BufferedOutputStream(new FileOutputStream(new File(dest, curr.getName())));
							byte[] buf = new byte[8192];
							int numBytes;
							while ((numBytes = in.read(buf, 0, buf.length)) != -1) {
								out.write(buf, 0, numBytes);
							}
						}
					} finally {
						closeQuietly(out);
					}
				}
			} finally {
			    if (zip != null) zip.close();
			}
			return true;
		}
		return false;
	}

	/**
	 * Delete a file. If a directory, then do a best effort to delete the contents of the directory recursively.
	 *
	 * @param   file
	 * @return  whether or not the file or directory was totally deleted
	 */
	public static boolean delete(File file) {
		if (file != null) {
			if (file.isDirectory()) {
				boolean completeSuccess = true;
				File[] contents = file.listFiles();

				for (File f : contents) {
					if (!delete(f)) {
						completeSuccess = false;
					}
				}

				if (!file.delete()) {
					completeSuccess = false;
				}

				return completeSuccess;
			} else if (file.exists()) {
			    boolean successfulDelete = file.delete();
			    return successfulDelete;
			}
			return true;
		}
		return false;
	}

	public static boolean delete(File... files) {
		boolean completeSuccess = true;
		for (File f : files) {
			if (!delete(f)) {
				completeSuccess = false;
			}
		}
		return completeSuccess;
	}

	public static boolean explodeZipArchive(File zipPath) throws IOException {
		if (isZipArchive(zipPath)) {
			File dest = File.createTempFile("zipoutput", null, zipPath.getParentFile());
			dest.delete();
			dest.mkdirs();
			if (dest.isDirectory()) {
				if (extractZipArchive(zipPath, dest)) {
					// Create a back-up of the ZIP archive in the same directory.
					File backup = File.createTempFile(zipPath.getName(), ".bak", zipPath.getParentFile());
					backup.delete();
					boolean createdBackUp = copy(zipPath, backup);

					boolean successfulDelete = delete(zipPath);
					assert successfulDelete;
					if (successfulDelete) {
						boolean success = dest.renameTo(zipPath);
						if (success) {
							delete(backup);
							return true;
						} else {
							delete(dest);
							backup.renameTo(zipPath);
						}
					} else {
						// TODO: User should restore the copy from the temporary files folder. Do not attempt to restore.
					}
				}
			}
		}
		return false;
	}

	/**
	 * The caller may choose to ignore the thrown exception.
	 * @param c
	 * @return
	 */
	public static Throwable closeQuietly(Closeable c) {
		if (c != null) {
			try {
				c.close();
			} catch (Throwable t) {
				return t;
			}
		}
		return null;
	}

	public static void closeQuietly(Closeable... closeables) {
		for (Closeable c : closeables) {
			closeQuietly(c);
		}
	}

	/**
	 * Exploding a WAR or EAR archive means extracting it to a directory that is in the same place and has the same name
	 * as the original archive. Of course, the original archive is deleted.
	 *
	 * @param webappPath
	 * @return
	 * @throws IOException
	 */
	public static boolean explodeWebApp(File webappPath, boolean createBackUp) throws IOException {
		if (isWebApp(webappPath) && isZipArchive(webappPath)) {
			File backup = null;

			if (createBackUp) {
				backup = File.createTempFile("fortifywebapp", ".bak", webappPath.getParentFile());
				boolean overwrite = true;
				boolean createdCopy = copy(webappPath, backup, overwrite);
				if (!createdCopy) {
					return false;
				}
			}

			boolean success = explodeZipArchive(webappPath);
			if (success) {
				boolean isExplodedEAR = webappPath.isDirectory() && webappPath.getName().endsWith(".ear");
				if (isExplodedEAR) {
					File[] contents = webappPath.listFiles();
					boolean partialSuccess = false;
					boolean noWebApps = true;

					for (File f : contents) {
						if (isWebApp(f) && isZipArchive(f)) {
							noWebApps = false;
							if (explodeWebApp(f, false)) {
								partialSuccess = true;
							}
						}
					}

					if (noWebApps || partialSuccess) {
						if (backup != null) delete(backup);
						return true;
					}
				}
			} else if (backup != null) {
				// Restore the back-up.
				delete(webappPath);
				copy(backup, webappPath, true);
				delete(backup);
			}

			return false;
		}
		return false;
	}

	public static boolean explodeWebApp(File webappPath) throws IOException {
		boolean createBackUp = true;
		return explodeWebApp(webappPath, createBackUp);
	}

	public static boolean isWebApp(File f) {
		return f != null && f.exists() && (f.getPath().endsWith(".ear") || f.getPath().endsWith(".war"));
	}
	
	public static boolean isFile(String path) {
		return path != null && new File(path).isFile();
	}
	
	/**
	 * Check if file path is directory or not. 
	 *
	 * @param path 
	 * @return whether or not the file path is directory
	 */
	public static boolean isDirectory(String path) {
		return path != null && new File(path).isDirectory();
	}
	
	/**
	 * Get all files under specified directory and its sub directories.
	 *
	 * @param path 
	 * @return List of file path
	 */
	public static List<String> getAllFilesInDirectory(String path)
	{
		List<String> fileList = new ArrayList<String>();
		File filePath = new File(path);
		String[] files = filePath.list();
		
		for (int i = 0; i < files.length; ++i )
		{
			File file = new File(path + File.separator + files[i]);
			if( file.isFile() )
			{
				fileList.add(file.getAbsolutePath());
			}
			
			if( file.isDirectory() )
			{
				List<String> filePaths = getAllFilesInDirectory(file.getAbsolutePath());
				fileList.addAll(filePaths);
			}
		}
		
		return fileList;
	}
	
	/**
	 * Delete all files in the directory tree rooted at rootDir that end in ".java".
	 * @param rootDir The root directory.
	 */
	public static void deleteJavaSources(File rootDir) {
		DirectoryWalker walker = new DirectoryWalker(rootDir);
		
		DirectoryWalker.FileProcessor<Void> deleter = new DirectoryWalker.FileProcessor<Void>() {
			@Override
			public void process(File f) {
				// Delete the file if it is a generated source.
				if (!f.isDirectory() && f.getName().endsWith(".java")) {
					f.delete();
				}
			}
		};
		
		walker.walk(deleter);
	}
	
	public static boolean containsGeneratedSources(File rootDir) {
		DirectoryWalker walker = new DirectoryWalker(rootDir);
		
		DirectoryWalker.FileProcessor<Boolean> genSrcFinder = new DirectoryWalker.FileProcessor<Boolean>() {
			@Override
			public void process(File f) throws DirectoryWalker.DoneException {
				if (!f.isDirectory() && f.getName().endsWith(".java")) {
					throw new DirectoryWalker.DoneException(Boolean.TRUE);
				}
			}
			
			@Override
			public Boolean getDefaultValue() { return Boolean.FALSE; }
		};
		
		boolean foundIt = walker.walk(genSrcFinder);
		return foundIt;
	}
	public static boolean containsClassFiles(File rootDir) {
		DirectoryWalker walker = new DirectoryWalker(rootDir);
		
		DirectoryWalker.FileProcessor<Boolean> genSrcFinder = new DirectoryWalker.FileProcessor<Boolean>() {
			@Override
			public void process(File f) throws DirectoryWalker.DoneException {
				if (!f.isDirectory() && f.getName().endsWith(".class")) {
					throw new DirectoryWalker.DoneException(Boolean.TRUE);
				}
			}
			
			@Override
			public Boolean getDefaultValue() { return Boolean.FALSE; }
		};
		
		boolean foundIt = walker.walk(genSrcFinder);
		return foundIt;
	}

	public static void appendTextAsAnotherLine(File f, String text) throws IOException {
		PrintWriter writer = null;
		boolean append = true;
		try {
			writer = new PrintWriter(new BufferedOutputStream(new FileOutputStream(f, append)));
			writer.println(text);
		} finally {
			closeQuietly(writer);
		}
	}

	/**
	 * Append a particular string to a file. If the file does not already exist, create it. The default character set
	 * is used.
	 *
	 * @param file
	 * @param msg
	 */
	public static boolean appendToFile(File file, String msg) {
		// This method may need to be revisited if there are character set issues.
		
		try {
			file.createNewFile(); // Creates a new file by this abstract path name iff one does not already exist.
		} catch (IOException e) {
			return false;
		}
		
		Writer writer = null;
		
		try {
			writer = new FileWriter(file, true);
			writer.append(msg);
			return true;
		} catch (IOException e) {
			return false;
		} finally {
			closeQuietly(writer);
		}
	}
	
	public static String getPathString(String... pathFragments) {
		if (pathFragments.length > 0) {
			String pathString = pathFragments[0];
			for (int i = 1; i < pathFragments.length; i++) {
				pathString += File.separator + pathFragments[i];
			}
			return pathString;
		}
		return "";
	}
	
	public static File getOrCreateDirectory(String... pathFragments) {
		File dir = new File(getPathString(pathFragments));
		if (!dir.isDirectory()) {
			dir.mkdirs();
		}
		return dir;
	}
	
	public static String readFirstLine(File f) throws IOException {
		BufferedReader reader = null;
		try {
			reader = new BufferedReader(new FileReader(f));
			return reader.readLine();
		} finally {
			closeQuietly(reader);
		}
	}

	public static File createZipArchive(String zipPath, Map<String, String> contents) throws IOException {
		File zip = new File(zipPath);
		if (zip.exists()) {
			delete(zip);
		}
		File parent = zip.getParentFile();
		if (!parent.isDirectory()) {
			parent.mkdirs();
		}

		if (contents.entrySet().size() > 0) {
			ZipOutputStream zos = null;
			PrintWriter writer = null;

			try {
				zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zip)));
				writer = new PrintWriter(zos);
				for (Map.Entry<String, String> entry : contents.entrySet()) {
					String key = entry.getKey();
					String value = entry.getValue();
					zos.putNextEntry(new ZipEntry(key));
					writer.write(value);
					writer.flush();
				}
			} finally {
				closeQuietly(zos);
			}
		}

		return zip;
	}

	public static File createZipArchive(String zipPath, File... contents) throws IOException {
		File zip = makeFreshFile(zipPath);

		if (contents.length > 0) {
			ZipOutputStream zos = null;
			try {
				zos = new ZipOutputStream(new BufferedOutputStream(new FileOutputStream(zipPath)));
				for (File f : contents) {
					zos.putNextEntry(new ZipEntry(f.getName()));
					BufferedInputStream is = new BufferedInputStream(new FileInputStream(f));
					byte[] buffer = new byte[8196];
					int numRead;
					while ((numRead = is.read(buffer, 0, buffer.length)) != -1) {
					    zos.write(buffer, 0, numRead);
					}
				}
			} finally {
					closeQuietly(zos);
			}
		}

		return zip;
	}

	public static File nonExistentFile(String... pathFragments) {
		File dest = new File(getPathString(pathFragments));
		delete(dest);
		return dest;
	}

	public static File makeFreshFile(String... pathFragments) throws IOException {
		File f = nonExistentFile(pathFragments);
		File parent = f.getParentFile();
		if (!parent.isDirectory()) {
			if (parent.exists()) {
				parent.delete();
			}
			parent.mkdirs();
		}
			f.createNewFile();
		return f;
	}
}
