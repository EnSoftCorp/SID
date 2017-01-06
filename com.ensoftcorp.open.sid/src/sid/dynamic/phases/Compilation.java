package sid.dynamic.phases;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.TreeSet;

import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.IJavaProject;
//import org.eclipse.jdt.core.IJavaProject;
//import org.eclipse.jdt.core.IPackageFragmentRoot;
//import org.eclipse.jdt.core.JavaCore;
//import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;

import com.ensoftcorp.abp.common.soot.ConfigManager;
import com.ensoftcorp.open.commons.utilities.DisplayUtils;

import sid.log.Log;
import soot.G;
import soot.SootClass;
import soot.util.Chain;

public class Compilation {

	/**
	 * Compiles a Jimple in a project to an output JAR file
	 * @param project The project to compile
	 * @param outputJar The location to output the resulting jar
	 * @throws IOException
	 * @throws CoreException 
	 */
	public static void compile(IProject project, File outputJar) throws IOException, CoreException {
		compile(project, null, outputJar);
	}
	
	/**
	 * Compiles a Jimple in a project to an output JAR file
	 * @param project The project to compile
	 * @param jimpleDirectory The directory path containing the jimple source (example: "sootOutput" or "WEB-INF/jimple"), otherwise null
	 * @param outputJar The location to output the resulting jar
	 * @throws IOException
	 * @throws CoreException 
	 */
	public static void compile(IProject project, IFolder jimpleDirectory, File outputJar) throws IOException, CoreException {
		// make sure there is a directory to write the output to
		File outputDirectory = outputJar.getParentFile();
		if(!outputDirectory.exists()){
			outputDirectory.mkdirs();
		}

		File projectDirectory = project.getLocation().toFile();
		
		// if the jimple is located entirely within a subdirectory of the project
		File inputDirectory = projectDirectory;
		if(jimpleDirectory != null){
			inputDirectory = jimpleDirectory.getLocation().toFile();
		}
		
		// locate classpath jars
		StringBuilder classpath = new StringBuilder();
		addJarsToClasspath(JavaCore.create(project), classpath);
		
		// locate classpath jars for project dependencies
		for(IProject dependency : project.getReferencedProjects()){
			addJarsToClasspath(JavaCore.create(dependency), classpath);
		}
		
		// configure soot arguments
		ArrayList<String> argList = new ArrayList<String>();
		argList.add("-src-prec"); argList.add("jimple");
//		argList.add("--xml-attributes");
		argList.add("-f"); argList.add("class");
		argList.add("-cp"); argList.add(classpath.toString());
//		argList.add("-allow-phantom-refs");
		argList.add("-output-dir"); argList.add(outputJar.getCanonicalPath()); argList.add("-output-jar");
		argList.add("-process-dir"); argList.add(inputDirectory.getCanonicalPath());
		argList.add("-include-all");
		String[] sootArgs = argList.toArray(new String[argList.size()]);

		// run soot to compile jimple
		try {
			ConfigManager.getInstance().startTempConfig();
			G.reset();
			soot.Main.v().run(sootArgs);
		} catch (Throwable t){
			DisplayUtils.showError(t, "An error occurred processing Jimple.\n\nSoot Classpath: " + Arrays.toString(sootArgs));
			Log.error("An error occurred processing Jimple.\n\nSoot Classpath: " + Arrays.toString(sootArgs), t);
		} finally {
			// restore the saved config (even if there was an error)
            ConfigManager.getInstance().endTempConfig();
		}

		// warn about any phantom references
		Chain<SootClass> phantomClasses = soot.Scene.v().getPhantomClasses();
        if (!phantomClasses.isEmpty()) {
            TreeSet<String> missingClasses = new TreeSet<String>();
            for (SootClass sootClass : phantomClasses) {
                    missingClasses.add(sootClass.toString());
            }
            StringBuilder message = new StringBuilder();
            message.append("When compiling Jimple, some classes were referenced that could not be found.\n\n");
            for (String sootClass : missingClasses) {
                    message.append(sootClass);
                    message.append("\n");
            }
            Log.warning(message.toString());
        }
	}

	// helper method for locating and adding the jar locations to the classpath
	// should handle library paths and absolute jar locations
	private static void addJarsToClasspath(IJavaProject jProject, StringBuilder classpath) throws JavaModelException {
		IPackageFragmentRoot[] fragments = jProject.getAllPackageFragmentRoots();
		for(IPackageFragmentRoot fragment : fragments){
			if(fragment.getKind() == IPackageFragmentRoot.K_BINARY){				
				String jarLocation;
				try {
					// get the path to the jar resource
					jarLocation = fragment.getResource().getLocation().toFile().getCanonicalPath();
				} catch (Exception e){
					try {
						// if its a library then the first try will fail
						jarLocation = fragment.getPath().toFile().getCanonicalPath();
					} catch (Exception e2){
						// just get the name of the jar
						jarLocation = fragment.getElementName();
					}
				}
				classpath.append(jarLocation);
				classpath.append(File.pathSeparator);
			}
		}
	}

}
