package tca.analysis.dynamically;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedList;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import tca.instruments.Instrument;

public class Instrumentation {

	/**
	 * Instruments the project with the given instrumentation
	 * 
	 * Step 1) Add the required jimple instrument implementations to the project
	 * Step 2) Insert the instrumentation requests for each of the given instruments
	 * @param instruments The dynamic operations to perform
	 * @throws CoreException 
	 * @throws IOException 
	 * @return Returns the IFolder indicating the location of the Jimple
	 */
	public static IFolder instrument(IProject clone, IProject jimpleInstruments, Collection<Instrument> instruments) throws CoreException, IOException {
		IProgressMonitor m = new NullProgressMonitor();
		File cloneProjectDirectory = clone.getLocation().toFile().getCanonicalFile();
		
		// lazily adding all jimple instrument implementations to the project
		// in the future this could be done only as needed for the requested instrument types
		File jimpleDirectory = getJimpleDirectory(cloneProjectDirectory);
		LinkedList<File> jimpleInstrumentFiles = findJimple(jimpleInstruments.getLocation().toFile());
		for(File jimpleInstrumentFile : jimpleInstrumentFiles){
			copyFile(jimpleInstrumentFile, new File(jimpleDirectory.getCanonicalPath() + File.separatorChar + jimpleInstrumentFile.getName()));
		}
		
		// make the jimple edits for each instrument
		if(instruments != null){
			for(Instrument instrument : instruments){
				instrument.performInstrumentation();
			}
		}
		
		// TODO: insert any drivers
		
		// refresh the project, just so everything is in sync
		clone.refreshLocal(IResource.DEPTH_INFINITE, m);

		// return the jimple directory
		return clone.getFolder(cloneProjectDirectory.toURI().relativize(new File(jimpleDirectory.getCanonicalPath()).toURI()).getPath());
	}
	
	// helper method for finding the jimple directory of the project
	// returns the common parent of all discovered jimple files
	private static File getJimpleDirectory(File projectDirectory) throws IOException {
		LinkedList<File> jimpleFiles = findJimple(projectDirectory);
		return commonParent(jimpleFiles.toArray(new File[jimpleFiles.size()]));
	}
	
	// a helper method for finding the common parent of a set of files
	// modified source from http://rosettacode.org/wiki/Find_common_directory_path
	private static File commonParent(File[] files) throws IOException {
		String[] paths = new String[files.length];
		for(int i=0; i<files.length; i++){
			if(files[i].isDirectory()){
				paths[i] = files[i].getCanonicalPath();
			} else {
				paths[i] = files[i].getParentFile().getCanonicalPath();
			}
		}
		String commonPath = "";
		String[][] folders = new String[paths.length][];
		for (int i = 0; i < paths.length; i++){
			folders[i] = paths[i].split(File.separator);
		}
		for (int j = 0; j < folders[0].length; j++){
			String thisFolder = folders[0][j];
			boolean allMatched = true;
			for (int i = 1; i < folders.length && allMatched; i++){
				if (folders[i].length < j){
					allMatched = false;
					break;
				}
				// otherwise
				allMatched &= folders[i][j].equals(thisFolder);
			}
			if (allMatched){
				commonPath += thisFolder + File.separatorChar;
			} else {
				break;
			}
		}
		return new File(commonPath);
	}
	
	// helper method for recursively finding jar files in a given directory
	private static LinkedList<File> findJimple(File directory){
		LinkedList<File> jimple = new LinkedList<File>();
		if(directory.exists()){
			if (directory.isDirectory()) {
				for (File f : directory.listFiles()) {
					jimple.addAll(findJimple(f));
				}
			}
			File file = directory;
			if(file.getName().endsWith(".jimple")){
				jimple.add(file);
			}
		}
		return jimple;
	}
	
	// helper method to copy a file from source to destination
	private static void copyFile(File from, File to) throws IOException {
		Files.copy(from.toPath(), to.toPath());
	}
}
