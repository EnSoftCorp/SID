package com.ensoftcorp.open.sid.handlers;

import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Scanner;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.launching.IVMInstall;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jdt.launching.LibraryLocation;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IFileEditorInput;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.handlers.HandlerUtil;

import com.ensoftcorp.atlas.core.db.graph.GraphElement;
import com.ensoftcorp.atlas.core.db.graph.Node;
import com.ensoftcorp.atlas.core.db.set.AtlasSet;
import com.ensoftcorp.atlas.core.query.Q;
import com.ensoftcorp.atlas.core.script.Common;
import com.ensoftcorp.atlas.core.script.StyledResult;
import com.ensoftcorp.atlas.core.xcsg.XCSG;
import com.ensoftcorp.atlas.ui.selection.SelectionUtil;
import com.ensoftcorp.atlas.ui.selection.event.IAtlasSelectionEvent;
import com.ensoftcorp.open.commons.utilities.DisplayUtils;
import com.ensoftcorp.open.java.commons.analyzers.JavaProgramEntryPoints;
import com.ensoftcorp.open.jimple.commons.transform.Compilation;
import com.ensoftcorp.open.sid.dynamic.instruments.Instrument;
import com.ensoftcorp.open.sid.dynamic.phases.Cloning;
import com.ensoftcorp.open.sid.dynamic.phases.Instrumentation;
import com.ensoftcorp.open.sid.dynamic.phases.Setup;
import com.ensoftcorp.open.sid.statics.LoopCallGraph;

public class CreateMethodDriverProjectHandler extends AbstractHandler {
	public CreateMethodDriverProjectHandler() {
	}

	private IResource extractResource(IEditorPart editor) {
		IEditorInput input = editor.getEditorInput();
		if (!(input instanceof IFileEditorInput))
			return null;
		return ((IFileEditorInput) input).getFile();
	}
	
	public Object execute(ExecutionEvent event) throws ExecutionException {
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		IWorkbenchPage page = window.getActivePage();
		IEditorPart editor = page.getActiveEditor();
		IProject project = extractResource(editor).getProject();
		
		IAtlasSelectionEvent selection = SelectionUtil.getLastSelectionEvent();
		Q selectionQ = selection.getSelection();
		AtlasSet<Node> methodNodes = selectionQ.nodesTaggedWithAny(XCSG.Method).eval().nodes();

		if (methodNodes.isEmpty()) {
			MessageDialog.openInformation(window.getShell(), "TCA", "No methods selected for instrumentation!");
		} else if (methodNodes.size() > 1) {
			MessageDialog.openInformation(window.getShell(), "TCA",
					"Please only select one method at a time for targeted dynamic analysis!"
							+ "\nMethod children are automatically instrumented.\n");
		}
		
		// clone the project and add the jimple instruments
		IProject cloneProject = null;
		try {
			IProject dynamicSupportProject = Setup.getOrCreateDynamicSupportProject();
			if(!dynamicSupportProject.exists()){
				DisplayUtils.showMessage("Unable to locate DynamicSupport project.");
				return null;
			}
			if(project != null){
				cloneProject = Cloning.clone(project, "clone");
				Instrumentation.addInstruments(cloneProject, dynamicSupportProject);
			} else {
				DisplayUtils.showMessage("Invalid selection. Please select the project to clone.");
			}
		} catch (Exception e) {
			DisplayUtils.showError(e, "Error cloning project.");
			if(cloneProject != null){
				Setup.deleteProject(cloneProject);
			}
		}

		GraphElement method = methodNodes.getFirst();
		try {
			// get the method loop call graph for selected methods
			LoopCallGraph lcg = LoopCallGraph.getLoopCallGraph();
			StyledResult methodLCG = LoopCallGraph.getMethodLCG(method, lcg);
			HashMap<GraphElement,LinkedList<Instrument>> instruments = Instrumentation.instrumentContainedLoopHeaders(cloneProject, methodLCG.getResult());
			cloneProject.refreshLocal(IResource.DEPTH_INFINITE, new NullProgressMonitor());
			DisplayUtils.showMessage("Instrumented " + instruments.keySet().size() + " loop headers.");
		} catch (Exception e){
			DisplayUtils.showError(e, "Could not instrument project.");
		}
		
		try {
			String driverProjectName = Cloning.getUniqueProjectName(project.getName(), "driver");
			
			IProject dynamicSupportProject = Setup.getOrCreateDynamicSupportProject();
			if(!dynamicSupportProject.exists()){
				DisplayUtils.showMessage("Unable to locate DynamicSupport project.");
				return null;
			}
			
			File instrumentedBytecode = File.createTempFile(cloneProject.getName(), ".jar");
			try {
				File projectDirectory = cloneProject.getLocation().toFile().getCanonicalFile();
				File jimpleDirectory = Instrumentation.getJimpleDirectory(projectDirectory);
				IFolder jimpleFolder = cloneProject.getFolder(projectDirectory.toURI().relativize(new File(jimpleDirectory.getCanonicalPath()).toURI()).getPath());
				Compilation.compile(cloneProject, jimpleFolder.getLocation().toFile(), instrumentedBytecode);
			} catch (Throwable t){
				DisplayUtils.showError(t, "Error compiling Jimple in \"" + cloneProject.getName() + "\".");
				return null;
			}
			
			createDriver(driverProjectName, dynamicSupportProject, instrumentedBytecode, method);
		} catch (Exception e){
			DisplayUtils.showError(e, "Could not create driver project.");
		}
		
		return null;
	}

	private void createDriver(String driverProjectName, IProject dynamicSupportProject, File instrumentedBytecode, GraphElement method) throws Exception {
		IProject driverProject = null;
		try {
			// create an empty project
			IWorkspaceRoot workspaceRoot = ResourcesPlugin.getWorkspace().getRoot();
			driverProject = workspaceRoot.getProject(driverProjectName);
			driverProject.create(null);
			driverProject.open(null);

			// set the java nature
			IProjectDescription description = driverProject.getDescription();
			description.setNatureIds(new String[] { JavaCore.NATURE_ID });
			driverProject.setDescription(description, null);

			// create the java project
			IJavaProject jDriverProject = JavaCore.create(driverProject);

			// add a binary folder
			IFolder binFolder = driverProject.getFolder("bin");
			binFolder.create(false, true, null);
			jDriverProject.setOutputLocation(binFolder.getFullPath(), null);

			List<IClasspathEntry> entries = new ArrayList<IClasspathEntry>();
			IVMInstall vmInstall = JavaRuntime.getDefaultVMInstall();
			LibraryLocation[] locations = JavaRuntime.getLibraryLocations(vmInstall);
			for (LibraryLocation element : locations) {
				entries.add(JavaCore.newLibraryEntry(element.getSystemLibraryPath(), null, null));
			}
			
			// add instrumented bytecode to the classpath
			File libsDirectory = new File(driverProject.getLocation().toFile().getAbsolutePath() + File.separatorChar + "lib");
			libsDirectory.mkdirs();
			File bytecodeJar = new File(libsDirectory.getAbsolutePath() + File.separatorChar + "bytecode.jar");
			instrumentedBytecode.renameTo(bytecodeJar);
			instrumentedBytecode = bytecodeJar;
			String instrumentedBytecodeCanonicalPath = instrumentedBytecode.getCanonicalPath();
			String projectCanonicalPath = jDriverProject.getProject().getLocation().toFile().getCanonicalPath();
			String instrumentedBytecodeBasePath = instrumentedBytecodeCanonicalPath.substring(instrumentedBytecodeCanonicalPath.indexOf(projectCanonicalPath));
			String instrumentedBytecodeParentCanonicalPath = instrumentedBytecode.getCanonicalPath();
			String instrumentedBytecodeParentBasePath = instrumentedBytecodeParentCanonicalPath.substring(instrumentedBytecodeParentCanonicalPath.indexOf(projectCanonicalPath));
			entries.add(JavaCore.newLibraryEntry(new Path(instrumentedBytecodeBasePath), null, new Path(instrumentedBytecodeParentBasePath)));
			
			File newLibsDirectory = new File(jDriverProject.getProject().getLocation().toFile().getAbsolutePath() + File.separatorChar + "lib");
			newLibsDirectory.mkdirs();
			File dynamicSupportLibDirectory = new File(dynamicSupportProject.getLocation().toFile().getAbsolutePath() + File.separatorChar + "driver-lib");
			if(dynamicSupportLibDirectory.exists()){
				for(File jar : dynamicSupportLibDirectory.listFiles()){
					if(jar.getName().endsWith(".jar")){
						File copiedJar = new File(newLibsDirectory.getAbsolutePath() + File.separatorChar + jar.getName());
						Files.copy(jar.toPath(), copiedJar.toPath());
						String copiedJarCanonicalPath = copiedJar.getCanonicalPath();
						String driverProjectCanonicalPath = jDriverProject.getProject().getLocation().toFile().getCanonicalPath();
						String copiedJarBasePath = copiedJarCanonicalPath.substring(copiedJarCanonicalPath.indexOf(driverProjectCanonicalPath));
						String copiedJarParentCanonicalPath = instrumentedBytecode.getCanonicalPath();
						String copiedJarParentBasePath = copiedJarParentCanonicalPath.substring(copiedJarParentCanonicalPath.indexOf(driverProjectCanonicalPath));
						entries.add(JavaCore.newLibraryEntry(new Path(copiedJarBasePath), null, new Path(copiedJarParentBasePath)));
					}
				}
			}

			// add libs to project class path
			jDriverProject.setRawClasspath(entries.toArray(new IClasspathEntry[entries.size()]), null);
			
			// add a source folder
			IFolder sourceFolder = driverProject.getFolder("src");
			sourceFolder.create(false, true, null);

			// add source folder to compilation entries
			IPackageFragmentRoot root = jDriverProject.getPackageFragmentRoot(sourceFolder);
			IClasspathEntry[] oldEntries = jDriverProject.getRawClasspath();
			IClasspathEntry[] newEntries = new IClasspathEntry[oldEntries.length + 1];
			System.arraycopy(oldEntries, 0, newEntries, 0, oldEntries.length);
			newEntries[oldEntries.length] = JavaCore.newSourceEntry(root.getPath());
			jDriverProject.setRawClasspath(newEntries, null);
			
			// add the support classes and driver templates
			File dynamicSupportSrcDirectory = new File(dynamicSupportProject.getLocation().toFile().getAbsolutePath() + File.separatorChar + "driver-src");
			addSupportClasses(jDriverProject, sourceFolder, dynamicSupportSrcDirectory, method);
		} catch (Exception e){
			if(driverProject != null){
				Setup.deleteProject(driverProject);
			}
			throw e;
		}
	}
	
	public static void addSupportClasses(IJavaProject jDriverProject, IFolder sourceFolder, File directory, GraphElement method) throws FileNotFoundException, JavaModelException {
		for(File file : directory.listFiles()){
			if(file.isDirectory()){
				addSupportClasses(jDriverProject, sourceFolder, file, method);
			} else if(file.getName().endsWith(".java")){
				String pkgName = file.getParentFile().getName(); // TODO: will have to do something fancier for nested package levels deeper than 1
				Scanner scanner = new Scanner(file);
				scanner.useDelimiter("\\Z"); 
				String content = scanner.next();
				
				// add callsites to all potential main methods
				String TCA_MAIN_METHOD_INJECTION_SITE = "TCA_MAIN_METHODS";
				String TCA_TARGET_METHOD_INJECTION_SITE = "TCA_TARGET_METHOD_CALLSITE";
				StringBuilder callsites = new StringBuilder();

				if(content.contains(TCA_TARGET_METHOD_INJECTION_SITE)){
					content = content.replace(TCA_TARGET_METHOD_INJECTION_SITE, "// " + createMethodDriverCallsite(method));
				}
				
				if(content.contains(TCA_MAIN_METHOD_INJECTION_SITE)){
					AtlasSet<Node> mainMethods = JavaProgramEntryPoints.findMainMethods().eval().nodes();
					for(Node mainMethod : mainMethods){
						callsites.append("				// " + createMainMethodCallsite(mainMethod) + "\n");
					}
				}
				content = content.replace(TCA_MAIN_METHOD_INJECTION_SITE, callsites.toString());
				
				scanner.close();
				IPackageFragment pkg = jDriverProject.getPackageFragmentRoot(sourceFolder).createPackageFragment(pkgName, false, null);
				pkg.createCompilationUnit(file.getName(), content, false, null);
			}
		}
	}
	
	/**
	 * Creates a string representing a source code callsite to the given method
	 * with an Object array for parameters
	 * 
	 * @param method
	 * @return
	 */
	public static String createMethodDriverCallsite(GraphElement method) {
		String result = getQualifiedName(method);
		result += "(";
		HashMap<Integer, GraphElement> parameterTypes = new HashMap<Integer, GraphElement>();
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		Q typeOfEdges = Common.universe().edgesTaggedWithAny(XCSG.TypeOf);
		Q params = containsEdges.forwardStep(Common.toQ(method)).nodesTaggedWithAny(XCSG.Parameter);
		for (GraphElement param : params.eval().nodes()) {
			int paramIndex = (Integer) param.getAttr(XCSG.parameterIndex) + 1;
			GraphElement paramType = typeOfEdges.successors(Common.toQ(param)).eval().nodes().getFirst();
			parameterTypes.put(paramIndex, paramType);
		}
		String prefix = "";
		for (int i = 1; i <= parameterTypes.size(); i++) {
			GraphElement paramType = parameterTypes.get(i);
			result += prefix + "(" + getQualifiedName(paramType) + ") " + "parameters[" + (i - 1) + "]";
			prefix = ", ";
		}
		result += ");";
		return result;
	}
	
	public static String createMainMethodCallsite(GraphElement method){
		return getQualifiedName(method) + "(new String[]{});";
	}
	
	// helper method to get a qualified name of a method
	public static String getQualifiedName(GraphElement node){
		String name = node.getAttr(XCSG.name).toString();
		// qualify the label
		Q containsEdges = Common.universe().edgesTaggedWithAny(XCSG.Contains);
		GraphElement parent = containsEdges.predecessors(Common.toQ(node)).eval().nodes().getFirst();
		while(parent != null && !parent.tags().contains(XCSG.Project) && !parent.tags().contains(XCSG.Library)){
			// skip adding qualified part for default package
			if(!(parent.tags().contains(XCSG.Package) && parent.getAttr(XCSG.name).toString().equals(""))){
				name = parent.getAttr(XCSG.name).toString() + "." + name;
			}
			parent = containsEdges.predecessors(Common.toQ(parent)).eval().nodes().getFirst();
		}
		return name;
	}

}