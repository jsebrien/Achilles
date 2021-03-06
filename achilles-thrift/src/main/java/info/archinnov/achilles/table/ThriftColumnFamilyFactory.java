/**
 *
 * Copyright (C) 2012-2013 DuyHai DOAN
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package info.archinnov.achilles.table;

import static info.archinnov.achilles.serializer.ThriftSerializerUtils.*;
import static info.archinnov.achilles.table.TableCreator.*;
import static me.prettyprint.hector.api.ddl.ComparatorType.*;
import info.archinnov.achilles.counter.AchillesCounter;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.serializer.ThriftSerializerTypeInferer;
import me.prettyprint.hector.api.Serializer;
import me.prettyprint.hector.api.ddl.ColumnFamilyDefinition;
import me.prettyprint.hector.api.ddl.ComparatorType;
import me.prettyprint.hector.api.factory.HFactory;

import org.apache.cassandra.utils.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ThriftColumnFamilyFactory {

	public static final String ENTITY_COMPARATOR_TYPE_ALIAS = "(org.apache.cassandra.db.marshal.BytesType,org.apache.cassandra.db.marshal.UTF8Type,org.apache.cassandra.db.marshal.UTF8Type)";
	public static final String ENTITY_COMPARATOR_TYPE_CHECK = "CompositeType(org.apache.cassandra.db.marshal.BytesType,org.apache.cassandra.db.marshal.UTF8Type,org.apache.cassandra.db.marshal.UTF8Type)";

	public static final String COUNTER_KEY_ALIAS = "(org.apache.cassandra.db.marshal.UTF8Type,org.apache.cassandra.db.marshal.UTF8Type)";
	public static final String COUNTER_KEY_CHECK = "org.apache.cassandra.db.marshal.CompositeType(org.apache.cassandra.db.marshal.UTF8Type,org.apache.cassandra.db.marshal.UTF8Type)";
	public static final String COUNTER_COMPARATOR_TYPE_ALIAS = "(org.apache.cassandra.db.marshal.UTF8Type)";
	public static final String COUNTER_COMPARATOR_CHECK = "CompositeType(org.apache.cassandra.db.marshal.UTF8Type)";

	protected static final Logger log = LoggerFactory.getLogger(ACHILLES_DDL_SCRIPT);

	private ThriftComparatorTypeAliasFactory comparatorAliasFactory = new ThriftComparatorTypeAliasFactory();

	public ColumnFamilyDefinition createEntityCF(EntityMeta entityMeta, String keyspaceName) {

		String entityName = entityMeta.getClassName();
		String columnFamilyName = entityMeta.getTableName();

		ColumnFamilyDefinition cfDef = HFactory.createColumnFamilyDefinition(keyspaceName, columnFamilyName,
				ComparatorType.COMPOSITETYPE);

		Pair<String, String> keyValidationClassAndAlias = comparatorAliasFactory.determineKeyValidationAndAlias(
				entityMeta.getIdMeta(), true);

		cfDef.setKeyValidationClass(keyValidationClassAndAlias.left);
		if (keyValidationClassAndAlias.right != null)
			cfDef.setKeyValidationAlias(keyValidationClassAndAlias.right);

		cfDef.setComparatorTypeAlias(ENTITY_COMPARATOR_TYPE_ALIAS);
		cfDef.setDefaultValidationClass(STRING_SRZ.getComparatorType().getTypeName());
		cfDef.setComment("Column family for entity '" + entityName + "'");

		StringBuilder builder = new StringBuilder("\n\n");
		builder.append("Create column family for entity ");
		builder.append("'").append(entityName).append("' : \n");
		builder.append("\tcreate column family ").append(columnFamilyName).append("\n");
		builder.append("\t\twith key_validation_class = ").append(keyValidationClassAndAlias.left);
		if (keyValidationClassAndAlias.right != null) {
			builder.append(keyValidationClassAndAlias.right);
		}
		builder.append("\n");
		builder.append("\t\tand comparator = '").append(ENTITY_COMPARATOR_TYPE_CHECK).append("'\n");
		builder.append("\t\tand default_validation_class = ").append(ComparatorType.UTF8TYPE.getTypeName())
				.append("\n");
		builder.append("\t\tand comment = 'Column family for entity ").append(entityName).append("'\n\n");

		log.debug(builder.toString());

		return cfDef;
	}

	public ColumnFamilyDefinition createClusteredEntityCF(String keyspaceName, EntityMeta entityMeta) {

		String tableName = entityMeta.getTableName();
		String entityName = entityMeta.getClassName();

		String defaultValidationType;
		if (entityMeta.isValueless()) {
			defaultValidationType = STRING_SRZ.getComparatorType().getTypeName();
		} else {
			PropertyMeta pm = entityMeta.getFirstMeta();
			Class<?> valueClass = pm.getValueClass();
			Serializer<?> valueSerializer;
			if (pm.isCounter()) {
				valueSerializer = LONG_SRZ;
				defaultValidationType = COUNTERTYPE.getTypeName();
			} else {
				valueSerializer = ThriftSerializerTypeInferer.getSerializer(valueClass);
				defaultValidationType = valueSerializer.getComparatorType().getTypeName();
			}
		}

		PropertyMeta idMeta = entityMeta.getIdMeta();
		Pair<String, String> keyValidationClassAndAlias = comparatorAliasFactory.determineKeyValidationAndAlias(
				entityMeta.getIdMeta(), true);
		String comparatorTypesAlias = comparatorAliasFactory.determineCompatatorTypeAliasForClusteringComponents(
				idMeta, true);

		ColumnFamilyDefinition cfDef = HFactory.createColumnFamilyDefinition(keyspaceName, tableName,
				ComparatorType.COMPOSITETYPE);

		cfDef.setKeyValidationClass(keyValidationClassAndAlias.left);
		if (keyValidationClassAndAlias.right != null)
			cfDef.setKeyValidationAlias(keyValidationClassAndAlias.right);
		cfDef.setComparatorTypeAlias(comparatorTypesAlias);

		cfDef.setDefaultValidationClass(defaultValidationType);
		cfDef.setComment("Column family for clustered entity '" + entityName + "'");

		StringBuilder builder = new StringBuilder("\n\n");
		builder.append("Create column family for clustered entity '");
		builder.append(entityName).append("' : \n");
		builder.append("\tcreate column family ").append(tableName).append("\n");
		builder.append("\t\twith key_validation_class = ").append(keyValidationClassAndAlias.left);
		if (keyValidationClassAndAlias.right != null) {
			builder.append(keyValidationClassAndAlias.right);
		}
		builder.append("\n");
		builder.append("\t\tand comparator = '").append(ComparatorType.COMPOSITETYPE.getTypeName());
		builder.append(comparatorTypesAlias).append("'\n");
		builder.append("\t\tand default_validation_class = ").append(defaultValidationType).append("\n");
		builder.append("\t\tand comment = 'Column family for clustered entity").append(entityName).append("'\n\n");

		log.debug(builder.toString());

		return cfDef;
	}

	public ColumnFamilyDefinition createCounterCF(String keyspaceName) {
		ColumnFamilyDefinition counterCfDef = HFactory.createColumnFamilyDefinition(keyspaceName,
				AchillesCounter.THRIFT_COUNTER_CF, COMPOSITETYPE);

		counterCfDef.setKeyValidationClass(COMPOSITETYPE.getTypeName());
		counterCfDef.setKeyValidationAlias(COUNTER_KEY_ALIAS);
		counterCfDef.setDefaultValidationClass(COUNTERTYPE.getClassName());
		counterCfDef.setComparatorTypeAlias(COUNTER_COMPARATOR_TYPE_ALIAS);

		counterCfDef.setComment("Generic Counter Column Family for Achilles");

		StringBuilder builder = new StringBuilder("\n\n");
		builder.append("Create generic counter column family for Achilles : \n");
		builder.append("\tcreate column family ").append(AchillesCounter.THRIFT_COUNTER_CF).append("\n");
		builder.append("\t\twith key_validation_class = '").append(COUNTER_KEY_CHECK).append("'\n");
		builder.append("\t\tand comparator = '").append(COUNTER_COMPARATOR_CHECK).append("'\n");
		builder.append("\t\tand default_validation_class = ").append(COUNTERTYPE.getTypeName()).append("\n");
		builder.append("\t\tand comment = 'Generic Counter Column Family for Achilles'\n\n");

		log.debug(builder.toString());

		return counterCfDef;

	}
}
