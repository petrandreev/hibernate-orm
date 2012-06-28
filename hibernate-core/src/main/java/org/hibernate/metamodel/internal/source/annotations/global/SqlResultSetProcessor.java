/*
 * Hibernate, Relational Persistence for Idiomatic Java
 *
 * Copyright (c) 2011, Red Hat Inc. or third-party contributors as
 * indicated by the @author tags or express copyright attribution
 * statements applied by the authors.  All third-party contributions are
 * distributed under license by Red Hat Inc.
 *
 * This copyrighted material is made available to anyone wishing to use, modify,
 * copy, or redistribute it subject to the terms and conditions of the GNU
 * Lesser General Public License, as published by the Free Software Foundation.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY
 * or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU Lesser General Public License
 * for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this distribution; if not, write to:
 * Free Software Foundation, Inc.
 * 51 Franklin Street, Fifth Floor
 * Boston, MA  02110-1301  USA
 */
package org.hibernate.metamodel.internal.source.annotations.global;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;


import org.jboss.jandex.AnnotationInstance;
import org.jboss.logging.Logger;

import org.hibernate.LockMode;
import org.hibernate.MappingException;
import org.hibernate.engine.ResultSetMappingDefinition;
import org.hibernate.engine.query.spi.sql.NativeSQLQueryRootReturn;
import org.hibernate.engine.query.spi.sql.NativeSQLQueryScalarReturn;
import org.hibernate.internal.CoreMessageLogger;
import org.hibernate.internal.util.StringHelper;
import org.hibernate.metamodel.internal.source.annotations.AnnotationBindingContext;
import org.hibernate.metamodel.internal.source.annotations.util.JPADotNames;
import org.hibernate.metamodel.internal.source.annotations.util.JandexHelper;
import org.hibernate.metamodel.spi.binding.AttributeBinding;
import org.hibernate.metamodel.spi.binding.CompositeAttributeBinding;
import org.hibernate.metamodel.spi.binding.EntityBinding;
import org.hibernate.metamodel.spi.binding.ManyToOneAttributeBinding;
import org.hibernate.metamodel.spi.binding.SingularAssociationAttributeBinding;

/**
 * Binds <ul>
 * <li>{@link javax.persistence.SqlResultSetMapping}</li>
 * <li>{@link javax.persistence.SqlResultSetMappings}</li>
 * <li>{@link javax.persistence.EntityResult}</li>
 * <li>{@link javax.persistence.FieldResult}</li>
 * <li>{@link javax.persistence.ColumnResult}</li>
 * </ul>
 *
 * @author Strong Liu <stliu@hibernate.org>
 */
public class SqlResultSetProcessor {
	private static final CoreMessageLogger LOG = Logger.getMessageLogger(
			CoreMessageLogger.class,
			QueryProcessor.class.getName()
	);

	private SqlResultSetProcessor() {
	}

	public static void bind(final AnnotationBindingContext bindingContext) {
		List<AnnotationInstance> annotations = bindingContext.getIndex()
				.getAnnotations( JPADotNames.SQL_RESULT_SET_MAPPING );
		for ( final AnnotationInstance sqlResultSetMappingAnnotationInstance : annotations ) {
			bindSqlResultSetMapping( bindingContext, sqlResultSetMappingAnnotationInstance );
		}

		annotations = bindingContext.getIndex().getAnnotations( JPADotNames.SQL_RESULT_SET_MAPPINGS );
		for ( final AnnotationInstance sqlResultSetMappingsAnnotationInstance : annotations ) {
			for ( AnnotationInstance annotationInstance : JandexHelper.getValue(
					sqlResultSetMappingsAnnotationInstance,
					"value",
					AnnotationInstance[].class
			) ) {
				bindSqlResultSetMapping( bindingContext, annotationInstance );
			}
		}
	}

	private static int entityAliasIndex = 0;

	private static void bindSqlResultSetMapping(final AnnotationBindingContext bindingContext, final AnnotationInstance annotation) {
		entityAliasIndex = 0;
		final String name = JandexHelper.getValue( annotation, "name", String.class );
		final ResultSetMappingDefinition definition = new ResultSetMappingDefinition( name );
		for ( final AnnotationInstance entityResult : JandexHelper.getValue(
				annotation,
				"entities",
				AnnotationInstance[].class
		) ) {
			bindEntityResult( bindingContext, entityResult, definition );
		}
		for ( final AnnotationInstance columnResult : JandexHelper.getValue(
				annotation,
				"columns",
				AnnotationInstance[].class
		) ) {
			bindColumnResult( bindingContext, columnResult, definition );
		}

		bindingContext.getMetadataImplementor().addResultSetMapping( definition );
	}

	private static void bindEntityResult(final AnnotationBindingContext bindingContext,
										 final AnnotationInstance entityResult,
										 final ResultSetMappingDefinition definition) {
		final Class entityClass = JandexHelper.getValue( entityResult, "entityClass", Class.class );
		final String className = entityClass.getName();
		//todo look up the whole entitybindings to find the right one seems stupid, but there is no way to look entitybinding
		//by class name, since with hbm, hibernate actually supports map one class to multi entities.
		final Iterable<EntityBinding> entityBindings = bindingContext.getMetadataImplementor().getEntityBindings();
		EntityBinding targetEntityBinding = null;
		for ( final EntityBinding entityBinding : entityBindings ) {
			if ( className.equals( entityBinding.getEntity().getClass() ) ) {
				targetEntityBinding = entityBinding;
				break;
			}
		}
		if ( targetEntityBinding == null ) {
			throw new MappingException(
					String.format(
							"Entity[%s] not found in SqlResultMapping[%s]",
							className,
							definition.getName()
					)
			);
		}

		final String discriminatorColumn = JandexHelper.getValue( entityResult, "discriminatorColumn", String.class );

		final Map<String, String[]> propertyResults = new HashMap<String, String[]>();


		if ( StringHelper.isNotEmpty( discriminatorColumn ) ) {
			final String quotingNormalizedName = bindingContext.getMetadataImplementor()
					.getObjectNameNormalizer()
					.normalizeIdentifierQuoting(
							discriminatorColumn
					);
			propertyResults.put( "class", new String[] { quotingNormalizedName } );
		}


		for ( final AnnotationInstance fieldResult : JandexHelper.getValue(
				entityResult,
				"fields",
				AnnotationInstance[].class
		) ) {
			bindFieldResult( bindingContext, targetEntityBinding, fieldResult, definition );
		}

		final NativeSQLQueryRootReturn result = new NativeSQLQueryRootReturn(
				"alias" + entityAliasIndex++,
				targetEntityBinding.getEntity().getName(),
				propertyResults,
				LockMode.READ
		);
		definition.addQueryReturn( result );
	}

	private static void bindFieldResult(final AnnotationBindingContext bindingContext,
										final EntityBinding entityBinding,
										final AnnotationInstance fieldResult,
										final ResultSetMappingDefinition definition) {
		final String name = JandexHelper.getValue( fieldResult, "name", String.class );
		final String column = JandexHelper.getValue( fieldResult, "column", String.class );
		final String quotingNormalizedColumnName = bindingContext.getMetadataImplementor().getObjectNameNormalizer()
				.normalizeIdentifierQuoting( column );
		if ( name.indexOf( '.' ) == -1 ) {

		}
		else {
			int dotIndex = name.lastIndexOf( '.' );
			String reducedName = name.substring( 0, dotIndex );
			AttributeBinding attributeBinding = entityBinding.locateAttributeBinding( reducedName );
			if ( CompositeAttributeBinding.class.isInstance( attributeBinding ) ) {
				CompositeAttributeBinding compositeAttributeBinding = CompositeAttributeBinding.class.cast(
						attributeBinding
				);
				compositeAttributeBinding.attributeBindings();
			}
			else if ( ManyToOneAttributeBinding.class.isInstance( attributeBinding ) ) {
				ManyToOneAttributeBinding manyToOneAttributeBinding = ManyToOneAttributeBinding.class.cast(
						attributeBinding
				);
				EntityBinding referencedEntityBinding = manyToOneAttributeBinding.getReferencedEntityBinding();
				Set<SingularAssociationAttributeBinding> referencingAttributeBindings = manyToOneAttributeBinding.getEntityReferencingAttributeBindings();
				//todo see org.hibernate.cfg.annotations.ResultsetMappingSecondPass#getSubPropertyIterator

			}
			else {
				throw new MappingException( "dotted notation reference neither a component nor a many/one to one" );
			}
		}


	}

	private static void bindColumnResult(final AnnotationBindingContext bindingContext,
										 final AnnotationInstance columnResult,
										 final ResultSetMappingDefinition definition) {
		final String name = JandexHelper.getValue( columnResult, "name", String.class );
		final String normalizedName = bindingContext.getMetadataImplementor()
				.getObjectNameNormalizer()
				.normalizeIdentifierQuoting( name );
		definition.addQueryReturn( new NativeSQLQueryScalarReturn( normalizedName, null ) );
	}
}
