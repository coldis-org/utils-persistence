package org.coldis.library.persistence.history;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.MirroredTypeException;
import javax.lang.model.type.TypeMirror;

import org.apache.commons.io.FileUtils;
import org.apache.velocity.Template;
import org.apache.velocity.VelocityContext;
import org.apache.velocity.app.VelocityEngine;
import org.apache.velocity.runtime.RuntimeConstants;
import org.apache.velocity.runtime.resource.loader.ClasspathResourceLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA entity history generator.
 */
@SupportedSourceVersion(value = SourceVersion.RELEASE_10)
@SupportedAnnotationTypes(value = { "org.coldis.library.persistence.history.HistoricalEntity" })
public class EntityHistoryGenerator extends AbstractProcessor {

	/**
	 * Logger.
	 */
	private static final Logger LOGGER = LoggerFactory.getLogger(EntityHistoryGenerator.class);

	/**
	 * TODO Javadoc
	 *
	 * @param  historicalEntityMetadata
	 * @throws IOException              Javadoc
	 */
	private void generateClasses(final HistoricalEntityMetadata historicalEntityMetadata) throws IOException {
		// Gets the velocity engine.
		final VelocityEngine velocityEngine = new VelocityEngine();
		// Configures the resource loader to also look at the classpath.
		velocityEngine.setProperty(RuntimeConstants.RESOURCE_LOADER, "classpath");
		velocityEngine.setProperty("classpath.resource.loader.class", ClasspathResourceLoader.class.getName());
		// Initializes the velocity engine.
		velocityEngine.init();
		// Creates a new velocity context and sets its variables.
		final VelocityContext velocityContext = new VelocityContext();
		// Sets the context values.
		velocityContext.put("h", "#");
		velocityContext.put("historicalEntity", historicalEntityMetadata);
		// Gets the templates for the entity history classes.
		final Template entityTemplate = velocityEngine.getTemplate(historicalEntityMetadata.getEntityTemplatePath());
		final Template repoTemplate = velocityEngine.getTemplate(historicalEntityMetadata.getDaoTemplatePath());
		final Template srvTemplate = velocityEngine.getTemplate(historicalEntityMetadata.getServiceTemplatePath());
		// Gets the writer for the generated classes.
		final File entityFile = new File(
				historicalEntityMetadata.getTargetPath() + File.separator
				+ historicalEntityMetadata.getEntityPackageName(),
				historicalEntityMetadata.getEntityTypeName() + ".java");
		final File daoFile = new File(
				historicalEntityMetadata.getTargetPath() + File.separator
				+ historicalEntityMetadata.getDaoPackageName(),
				historicalEntityMetadata.getDaoTypeName() + ".java");
		final File serviceFile = new File(
				historicalEntityMetadata.getTargetPath() + File.separator
				+ historicalEntityMetadata.getServicePackageName(),
				historicalEntityMetadata.getServiceTypeName() + ".java");
		FileUtils.forceMkdir(entityFile.getParentFile());
		FileUtils.forceMkdir(daoFile.getParentFile());
		FileUtils.forceMkdir(serviceFile.getParentFile());
		final Writer entityWriter = new FileWriter(entityFile);
		final Writer daoWriter = new FileWriter(daoFile);
		final Writer serviceWriter = new FileWriter(serviceFile);
		// Generates the classes.
		entityTemplate.merge(velocityContext, entityWriter);
		repoTemplate.merge(velocityContext, daoWriter);
		srvTemplate.merge(velocityContext, serviceWriter);
		// Closes the writers.
		entityWriter.close();
		daoWriter.close();
		serviceWriter.close();
	}

	/**
	 * TODO Javadoc
	 *
	 * @param  historicalEntity
	 * @return                  Javadoc
	 */
	private String getStateAttributeConverter(final HistoricalEntity historicalEntity) {
		// Entity state attribute converter.
		TypeMirror stateAttributeConverter = null;
		// This is a trick to get the class information (catching the
		// MirroredTypeException).
		try {
			historicalEntity.stateAttributeConverter();
		}
		// Catcher mirrored exception.
		catch (final MirroredTypeException exception) {
			// Gets the class information.
			stateAttributeConverter = exception.getTypeMirror();
		}
		// Returns the entity state attribute converter.
		return stateAttributeConverter.toString();
	}

	/**
	 * TODO Javadoc
	 *
	 * @param  entityType
	 * @return            Javadoc
	 */
	private HistoricalEntityMetadata getEntityHistoryMetadata(final TypeElement entityType) {
		// Gets the historical entity metadata.
		final HistoricalEntity historicalEntity = entityType.getAnnotation(HistoricalEntity.class); // Tries to get the
		// Creates the default metadata.
		final HistoricalEntityMetadata historicalEntityMetadata = new HistoricalEntityMetadata(
				historicalEntity.targetPath(), historicalEntity.entityTemplatePath(),
				historicalEntity.daoTemplatePath(), historicalEntity.serviceTemplatePath(),
				historicalEntity.basePackageName(),
				((PackageElement) entityType.getEnclosingElement()).getQualifiedName().toString(),
				entityType.getSimpleName().toString(), this.getStateAttributeConverter(historicalEntity),
				historicalEntity.stateColumnDefinition());
		// Returns the historical entity metadata.
		return historicalEntityMetadata;
	}

	/**
	 * @see javax.annotation.processing.AbstractProcessor#process(java.util.Set,
	 *      javax.annotation.processing.RoundEnvironment)
	 */
	@Override
	@SuppressWarnings("unchecked")
	public boolean process(final Set<? extends TypeElement> annotations, final RoundEnvironment roundEnv) {
		EntityHistoryGenerator.LOGGER.debug("Initializing EntityHistoryGenerator...");
		// For each historical entity.
		for (final TypeElement entityType : (Set<TypeElement>) roundEnv
				.getElementsAnnotatedWith(HistoricalEntity.class)) {
			EntityHistoryGenerator.LOGGER
			.debug("Generating entity history classes for '" + entityType.getSimpleName() + "'...");
			// Tries to generate the entity history classes.
			try {
				this.generateClasses(this.getEntityHistoryMetadata(entityType));
				EntityHistoryGenerator.LOGGER
				.debug("Historical entity '" + entityType.getSimpleName() + "' processed successfully.");
			}
			// If the historical entity could not be processed correctly.
			catch (final IOException exception) {
				// Logs the error.
				EntityHistoryGenerator.LOGGER.debug(
						"Historical entity '" + entityType.getSimpleName() + "' not processed successfully.",
						exception);
			}
		}
		// Returns that the annotations have been processed.
		EntityHistoryGenerator.LOGGER.debug("Finishing EntityHistoryGenerator...");
		return true;
	}

}
