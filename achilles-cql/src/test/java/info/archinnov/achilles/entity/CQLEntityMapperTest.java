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
package info.archinnov.achilles.entity;

import static info.archinnov.achilles.entity.metadata.PropertyType.*;
import static org.fest.assertions.api.Assertions.*;
import static org.mockito.Matchers.*;
import static org.mockito.Mockito.*;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.proxy.CQLRowMethodInvoker;
import info.archinnov.achilles.proxy.ReflectionInvoker;
import info.archinnov.achilles.test.builders.CompleteBeanTestBuilder;
import info.archinnov.achilles.test.builders.PropertyMetaTestBuilder;
import info.archinnov.achilles.test.mapping.entity.ClusteredEntity;
import info.archinnov.achilles.test.mapping.entity.CompleteBean;
import info.archinnov.achilles.test.parser.entity.EmbeddedKey;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cassandra.cql3.ColumnIdentifier;
import org.apache.cassandra.cql3.ColumnSpecification;
import org.apache.cassandra.db.marshal.LongType;
import org.apache.cassandra.db.marshal.UTF8Type;
import org.apache.commons.lang.math.RandomUtils;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

import com.datastax.driver.core.ColumnDefinitions;
import com.datastax.driver.core.ColumnDefinitions.Definition;
import com.datastax.driver.core.Row;
import com.google.common.collect.ImmutableMap;

@RunWith(MockitoJUnitRunner.class)
public class CQLEntityMapperTest {

	@InjectMocks
	private CQLEntityMapper entityMapper;

	@Mock
	private ReflectionInvoker invoker;

	@Mock
	private CQLRowMethodInvoker cqlRowInvoker;

	@Mock
	private Row row;

	@Mock
	private ColumnDefinitions columnDefs;

	@Mock
	private EntityMeta entityMeta;

	private Definition def1;
	private Definition def2;

	private CompleteBean entity = CompleteBeanTestBuilder.builder().randomId().buid();

	@Test
	public void should_set_eager_properties_to_entity() throws Exception {
		PropertyMeta pm = mock(PropertyMeta.class);
		when(pm.isEmbeddedId()).thenReturn(false);
		when(pm.getPropertyName()).thenReturn("name");

		List<PropertyMeta> eagerMetas = Arrays.asList(pm);

		when(entityMeta.getEagerMetas()).thenReturn(eagerMetas);

		when(row.isNull("name")).thenReturn(false);
		when(cqlRowInvoker.invokeOnRowForFields(row, pm)).thenReturn("value");

		entityMapper.setEagerPropertiesToEntity(row, entityMeta, entity);

		verify(pm).setValueToField(entity, "value");
	}

	@Test
	public void should_set_null_to_entity_when_no_value_from_row() throws Exception {
		PropertyMeta pm = mock(PropertyMeta.class);
		when(pm.isEmbeddedId()).thenReturn(false);
		when(pm.getPropertyName()).thenReturn("name");

		List<PropertyMeta> eagerMetas = Arrays.asList(pm);

		when(entityMeta.getEagerMetas()).thenReturn(eagerMetas);

		when(row.isNull("name")).thenReturn(true);

		entityMapper.setEagerPropertiesToEntity(row, entityMeta, entity);

		verify(pm, never()).setValueToField(eq(entity), any());
		verifyZeroInteractions(cqlRowInvoker);
	}

	@Test
	public void should_do_nothing_when_null_row() throws Exception {
		PropertyMeta pm = mock(PropertyMeta.class);

		entityMapper.setPropertyToEntity(null, pm, entity);

		verifyZeroInteractions(cqlRowInvoker, pm);
	}

	@Test
	public void should_set_compound_key_to_entity() throws Exception {
		PropertyMeta pm = PropertyMetaTestBuilder.completeBean(Void.class, String.class).field("name").accessors()
				.type(EMBEDDED_ID).compNames("name").invoker(invoker).build();

		EmbeddedKey embeddedKey = new EmbeddedKey();
		when(cqlRowInvoker.extractCompoundPrimaryKeyFromRow(row, pm, true)).thenReturn(embeddedKey);

		entityMapper.setPropertyToEntity(row, pm, entity);

		verify(invoker).setValueToField(entity, pm.getSetter(), embeddedKey);
	}

	@Test
	public void should_map_row_to_entity() throws Exception {
		Long id = RandomUtils.nextLong();
		PropertyMeta idMeta = mock(PropertyMeta.class);
		PropertyMeta valueMeta = mock(PropertyMeta.class);

		when(idMeta.isEmbeddedId()).thenReturn(false);

		Map<String, PropertyMeta> propertiesMap = ImmutableMap.of("id", idMeta, "value", valueMeta);

		ColumnIdentifier iden1 = new ColumnIdentifier(UTF8Type.instance.decompose("id"), UTF8Type.instance);
		ColumnSpecification spec1 = new ColumnSpecification("keyspace", "id", iden1, LongType.instance);

		ColumnIdentifier iden2 = new ColumnIdentifier(UTF8Type.instance.decompose("value"), UTF8Type.instance);
		ColumnSpecification spec2 = new ColumnSpecification("keyspace", "value", iden2, UTF8Type.instance);

		def1 = Whitebox.invokeMethod(Definition.class, "fromTransportSpecification", spec1);
		def2 = Whitebox.invokeMethod(Definition.class, "fromTransportSpecification", spec2);

		when(row.getColumnDefinitions()).thenReturn(columnDefs);
		when(columnDefs.iterator()).thenReturn(Arrays.asList(def1, def2).iterator());

		when(entityMeta.getIdMeta()).thenReturn(idMeta);
		when(entityMeta.instanciate()).thenReturn(entity);
		when(cqlRowInvoker.invokeOnRowForFields(row, idMeta)).thenReturn(id);
		when(cqlRowInvoker.invokeOnRowForFields(row, valueMeta)).thenReturn("value");
		when(entityMeta.instanciate()).thenReturn(entity);

		CompleteBean actual = entityMapper.mapRowToEntityWithPrimaryKey(CompleteBean.class, entityMeta, row,
				propertiesMap, true);

		assertThat(actual).isSameAs(entity);
		verify(idMeta).setValueToField(entity, id);
		verify(valueMeta).setValueToField(entity, "value");
	}

	@Test
	public void should_map_row_to_entity_with_primary_key() throws Exception {
		ClusteredEntity entity = new ClusteredEntity();
		EmbeddedKey embeddedKey = new EmbeddedKey();
		PropertyMeta idMeta = mock(PropertyMeta.class);

		when(idMeta.isEmbeddedId()).thenReturn(true);

		Map<String, PropertyMeta> propertiesMap = new HashMap<String, PropertyMeta>();

		when(row.getColumnDefinitions()).thenReturn(columnDefs);
		when(columnDefs.iterator()).thenReturn(Arrays.<Definition> asList().iterator());
		when(entityMeta.instanciate()).thenReturn(entity);
		when(entityMeta.getIdMeta()).thenReturn(idMeta);
		when(cqlRowInvoker.extractCompoundPrimaryKeyFromRow(row, idMeta, true)).thenReturn(embeddedKey);

		ClusteredEntity actual = entityMapper.mapRowToEntityWithPrimaryKey(ClusteredEntity.class, entityMeta, row,
				propertiesMap, true);

		assertThat(actual).isSameAs(entity);
		verify(idMeta).setValueToField(entity, embeddedKey);
	}

	@Test
	public void should_not_map_row_to_entity_with_primary_key_when_entity_null() {
		ClusteredEntity actual = entityMapper.mapRowToEntityWithPrimaryKey(ClusteredEntity.class, entityMeta, row,
				null, true);

		assertThat(actual).isNull();
	}

	@Test
	public void should_return_null_when_no_column_found() throws Exception {
		when(row.getColumnDefinitions()).thenReturn(null);
		when(entityMeta.instanciate()).thenReturn(entity);

		CompleteBean actual = entityMapper
				.mapRowToEntityWithPrimaryKey(CompleteBean.class, entityMeta, row, null, true);
		assertThat(actual).isNull();
	}

}
