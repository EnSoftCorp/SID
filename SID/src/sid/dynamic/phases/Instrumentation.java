package sid.dynamic.phases;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.LinkedList;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.open.commons.analysis.utils.StandardQueries;

import sid.dynamic.instruments.Instrument;
import sid.dynamic.instruments.counters.LoopIterationCounter;
import sid.dynamic.instruments.timers.LoopIterationTimer;
import sid.log.Log;
import sid.statics.LoopAnalyzer;

public class Instrumentation {

	/**
	 * Instruments the project with the given instrumentation
	 * 
	 * Adds or overwrites all the required jimple instrument implementations to the project
	 * @param instruments The dynamic operations to perform
	 * @throws CoreException 
	 * @throws IOException 
	 * @return Returns the IFolder indicating the location of the Jimple
	 */
	public static IFolder addInstruments(IProject clone, IProject dynamicSupportProject) throws CoreException, IOException {
		IProgressMonitor m = new NullProgressMonitor();
		File cloneProjectDirectory = clone.getLocation().toFile().getCanonicalFile();
		
		// lazily adding all jimple instrument implementations to the project
		// in the future this could be done only as needed for the requested instrument types
		File jimpleDirectory = getJimpleDirectory(cloneProjectDirectory);
		LinkedList<File> jimpleInstrumentFiles = findJimple(dynamicSupportProject.getLocation().toFile());
		for(File jimpleInstrumentFile : jimpleInstrumentFiles){
			copyFile(jimpleInstrumentFile, new File(jimpleDirectory.getCanonicalPath() + File.separatorChar + jimpleInstrumentFile.getName()));
		}
		
		// refresh the project, just so everything is in sync
		clone.refreshLocal(IResource.DEPTH_INFINITE, m);

		// return the jimple directory
		return clone.getFolder(cloneProjectDirectory.toURI().relativize(new File(jimpleDirectory.getCanonicalPath()).toURI()).getPath());
	}
	
	public static HashMap<GraphElement,LinkedList<Instrument>> instrumentAllLoopHeaders(String projectName){
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		return instrumentAllLoopHeaders(project);
	}
	
	public static HashMap<GraphElement,LinkedList<Instrument>> instrumentContainedLoopHeaders(String projectName, Q context){
		IProject project = ResourcesPlugin.getWorkspace().getRoot().getProject(projectName);
		return instrumentContainedLoopHeaders(project, context);
	}
	
	/**
	 * Returns a mapping of methods to the Instruments added to the loop headers contained in the method
	 * @param project
	 * @return
	 */
	public static HashMap<GraphElement,LinkedList<Instrument>> instrumentAllLoopHeaders(IProject project){
		return instrumentContainedLoopHeaders(project, Common.universe());
	}
	
	/**
	 * Returns a mapping of methods to the Instruments added to the loop headers (in a given context) contained in the method
	 * @param project
	 * @return
	 */
	public static HashMap<GraphElement,LinkedList<Instrument>> instrumentContainedLoopHeaders(IProject project, Q context){
		HashMap<GraphElement,LinkedList<Instrument>> result = new HashMap<GraphElement,LinkedList<Instrument>>();
		try {
			Log.info("Instrumenting all loop headers for " + project.getName() + " for the given context...");
			Q allLoopHeaders = context.contained().nodesTaggedWithAny(LoopAnalyzer.CFGNode.LOOP_HEADER);
			if(allLoopHeaders.eval().nodes().isEmpty()){
				LoopAnalyzer.analyzeLoops();
				allLoopHeaders = context.contained().nodesTaggedWithAny(LoopAnalyzer.CFGNode.LOOP_HEADER);
			}
			Q exceptionalLoopHeaders = allLoopHeaders.contained().nodesTaggedWithAny(XCSG.CaughtValue).containers().nodesTaggedWithAny(LoopAnalyzer.CFGNode.LOOP_HEADER);;
			Q safeLoopHeadersToInstrument = allLoopHeaders.difference(exceptionalLoopHeaders);
			for(Node loopHeader : safeLoopHeadersToInstrument.eval().nodes()){
				Instrument counterInstrument = new LoopIterationCounter(project, loopHeader);
				counterInstrument.performInstrumentation();
				Instrument timerInstrument = new LoopIterationTimer(project, loopHeader);
				timerInstrument.performInstrumentation();
				GraphElement method = StandardQueries.getContainingMethod(loopHeader);
				if(result.containsKey(method)){
					LinkedList<Instrument> instruments = result.get(method);
					instruments.add(counterInstrument);
					instruments.add(timerInstrument);
				} else {
					LinkedList<Instrument> instruments = new LinkedList<Instrument>();
					instruments.add(counterInstrument);
					instruments.add(timerInstrument);
					result.put(method, instruments);
				}
			}
		} catch (Exception e){
			Log.error(e.getMessage(), e);
		}
		return result;
	}
	
	// helper method for finding the jimple directory of the project
	// returns the common parent of all discovered jimple files
	public static File getJimpleDirectory(File projectDirectory) throws IOException {
		LinkedList<File> jimpleFiles = findJimple(projectDirectory);
		return commonParent(jimpleFiles.toArray(new File[jimpleFiles.size()]));
	}
	
	// a helper method for finding the common parent of a set of files
	// modified source from http://rosettacode.org/wiki/Find_common_directory_path
	private static File commonParent(File[] files) throws IOException {
		String delimeter = File.separator.equals("\\") ? "\\\\" : File.separator;
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
			folders[i] = paths[i].split(delimeter);
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
	
	// helper method to copy or overwrite a file from source to destination
	private static void copyFile(File from, File to) throws IOException {
		if(to.exists()){
			to.delete();
		}
		Files.copy(from.toPath(), to.toPath());
	}
}
