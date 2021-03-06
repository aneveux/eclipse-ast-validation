package net.atos.jdt.ast.validation.engine;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import net.atos.jdt.ast.validation.engine.internal.Activator;
import net.atos.jdt.ast.validation.engine.internal.ValidationEngineMessages;
import net.atos.jdt.ast.validation.engine.internal.extpt.ASTRulesExtensionPoint;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Status;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.CompilationUnit;

/**
 * Process that execute validation rules on ICompilationUnits
 * 
 * @author mvanbesien
 * @since 1.0
 */
public class ASTValidationEngine {
	/**
	 * List of Compilation Units to be managed by this process
	 */
	private Collection<ICompilationUnit> compilationUnits = new ArrayList<ICompilationUnit>();;

	/**
	 * Valid Repositories for this engine
	 */
	private final String[] validRepositories;

	/**
	 * Rules Source, used to retrieve the rules during execution
	 */
	private IASTRulesDataSource dataSource;

	/**
	 * Creates new Validation Engine for Compilation Units as from parameters
	 * 
	 * @param compilationUnits
	 * @param validRepositories
	 */
	public ASTValidationEngine(IASTRulesDataSource dataSource, final Collection<ICompilationUnit> compilationUnits,
			final String... validRepositories) {
		this.compilationUnits.clear();
		this.compilationUnits.addAll(compilationUnits);
		this.validRepositories = validRepositories;
		if (dataSource != null)
			this.dataSource = dataSource;
	}

	/**
	 * Creates new Validation Engine for Compilation Units as from parameters
	 * 
	 * @param compilationUnits
	 * @param validRepositories
	 */
	public ASTValidationEngine(final Collection<ICompilationUnit> compilationUnits, final String... validRepositories) {
		this.compilationUnits.clear();
		this.compilationUnits.addAll(compilationUnits);
		this.validRepositories = validRepositories;
		this.dataSource = ASTRulesExtensionPoint.getInstance();
	}

	/**
	 * Runs the validation
	 * 
	 * @param monitor
	 * @throws CoreException
	 */
	public void execute(final IProgressMonitor monitor) throws CoreException {
		for (final ICompilationUnit compilationUnit : this.compilationUnits) {
			this.execute(compilationUnit, monitor);
		}
	}

	/**
	 * Runs the validation on one specific compilation unit
	 * 
	 * @param compilationUnit
	 * @param monitor
	 * @throws CoreException
	 */
	private void execute(final ICompilationUnit compilationUnit, final IProgressMonitor monitor) throws CoreException {

		final List<ASTRulesRepository> repositories = this.dataSource.getRepositories(this.validRepositories);
		// At first remove the previous markers
		final IResource resource = compilationUnit.getResource();

		for (final ASTRulesRepository ruleRepository : repositories) {
			resource.deleteMarkers(ruleRepository.getMarkerId(), true, IResource.DEPTH_ZERO);

			for (final ASTRuleDescriptor ruleDescriptor : ruleRepository.getRules(compilationUnit)) {
				monitor.subTask(ValidationEngineMessages.VALIDATING_CU.value(compilationUnit.getElementName(),
						ruleDescriptor.getDescription()));
				final ASTParser parser = ASTParser.newParser(AST.JLS4);
				parser.setSource(compilationUnit);
				parser.setStatementsRecovery(true);
				parser.setResolveBindings(true);
				parser.setBindingsRecovery(false);
				final CompilationUnit domCompilationUnit = (CompilationUnit) parser
						.createAST(new NullProgressMonitor());
				try {
					domCompilationUnit.accept(ruleDescriptor.getRule());
				} catch (Exception e) {
					Activator
							.getDefault()
							.getLog()
							.log(new Status(IStatus.ERROR, Activator.PLUGIN_ID,
									"An exception was caught while executing rule <" + ruleDescriptor.getId()
											+ "> on <" + compilationUnit.getElementName() + ">", e));
					e.printStackTrace();
				}
			}
		}
	}
}
