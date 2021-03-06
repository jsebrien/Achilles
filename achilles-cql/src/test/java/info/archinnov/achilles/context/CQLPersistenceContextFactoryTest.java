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
package info.archinnov.achilles.context;

import static info.archinnov.achilles.entity.metadata.PropertyType.*;
import static info.archinnov.achilles.type.ConsistencyLevel.*;
import static org.fest.assertions.api.Assertions.*;
import static org.mockito.Mockito.*;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.entity.operations.CQLEntityProxifier;
import info.archinnov.achilles.proxy.ReflectionInvoker;
import info.archinnov.achilles.test.builders.PropertyMetaTestBuilder;
import info.archinnov.achilles.test.mapping.entity.CompleteBean;
import info.archinnov.achilles.type.OptionsBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.math.RandomUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.reflect.Whitebox;

@RunWith(MockitoJUnitRunner.class)
public class CQLPersistenceContextFactoryTest {

	private CQLPersistenceContextFactory pmf;

	@Mock
	private CQLDaoContext daoContext;

	@Mock
	private ConfigurationContext configContext;

	@Mock
	private CQLEntityProxifier proxifier;

	@Mock
	private ReflectionInvoker invoker;

	@Mock
	private CQLImmediateFlushContext flushContext;

	private Map<Class<?>, EntityMeta> entityMetaMap;

	private EntityMeta meta;

	private PropertyMeta idMeta;

	@Before
	public void setUp() throws Exception {
		meta = new EntityMeta();
		idMeta = PropertyMetaTestBuilder.completeBean(Void.class, Long.class).field("id").type(ID).accessors()
				.invoker(invoker).build();
		meta.setIdMeta(idMeta);
		meta.setEntityClass(CompleteBean.class);
		entityMetaMap = new HashMap<Class<?>, EntityMeta>();
		entityMetaMap.put(CompleteBean.class, meta);

		pmf = new CQLPersistenceContextFactory(daoContext, configContext, entityMetaMap);
		Whitebox.setInternalState(pmf, ReflectionInvoker.class, invoker);
	}

	@Test
	public void should_create_new_context_for_entity_with_consistency_and_ttl() throws Exception {
		Long primaryKey = RandomUtils.nextLong();
		CompleteBean entity = new CompleteBean(primaryKey);

		when((Class) proxifier.deriveBaseClass(entity)).thenReturn(CompleteBean.class);
		when(invoker.getPrimaryKey(entity, idMeta)).thenReturn(primaryKey);

		CQLPersistenceContext actual = pmf.newContext(entity, OptionsBuilder.withConsistency(EACH_QUORUM).ttl(95));

		assertThat(actual.getEntity()).isSameAs(entity);
		assertThat(actual.getPrimaryKey()).isSameAs(primaryKey);
		assertThat(actual.getPrimaryKey()).isSameAs(primaryKey);
		assertThat(actual.getEntityClass()).isSameAs((Class) CompleteBean.class);
		assertThat(actual.getEntityMeta()).isSameAs(meta);
		assertThat(actual.getIdMeta()).isSameAs(idMeta);
		assertThat(actual.getTtt().get()).isEqualTo(95);
	}

	@Test
	public void should_create_new_context_for_entity() throws Exception {
		Long primaryKey = RandomUtils.nextLong();
		CompleteBean entity = new CompleteBean(primaryKey);

		when((Class) proxifier.deriveBaseClass(entity)).thenReturn(CompleteBean.class);
		when(invoker.getPrimaryKey(entity, idMeta)).thenReturn(primaryKey);

		CQLPersistenceContext actual = pmf.newContext(entity);

		assertThat(actual.getEntity()).isSameAs(entity);
		assertThat(actual.getPrimaryKey()).isSameAs(primaryKey);
		assertThat(actual.getEntityClass()).isSameAs((Class) CompleteBean.class);
		assertThat(actual.getEntityMeta()).isSameAs(meta);
		assertThat(actual.getIdMeta()).isSameAs(idMeta);
		assertThat(actual.getTtt()).isSameAs(CQLPersistenceContextFactory.NO_TTL);
	}

	@Test
	public void should_create_new_context_with_primary_key() throws Exception {
		Object primaryKey = RandomUtils.nextLong();

		CQLPersistenceContext context = pmf.newContext(CompleteBean.class, primaryKey, OptionsBuilder
				.withConsistency(LOCAL_QUORUM).ttl(98));

		assertThat(context.getEntity()).isNull();
		assertThat(context.getPrimaryKey()).isSameAs(primaryKey);
		assertThat(context.getEntityClass()).isSameAs((Class) CompleteBean.class);
		assertThat(context.getEntityMeta()).isSameAs(meta);
		assertThat(context.getIdMeta()).isSameAs(idMeta);
		assertThat(context.getTtt().get()).isEqualTo(98);
	}

	@Test
	public void should_create_new_context_for_slice_query() throws Exception {
		Long primaryKey = RandomUtils.nextLong();
		List<Object> partitionComponents = Arrays.<Object> asList(primaryKey);
		when(invoker.instanciateEmbeddedIdWithPartitionComponents(idMeta, partitionComponents)).thenReturn(primaryKey);

		CQLPersistenceContext actual = pmf.newContextForSliceQuery(CompleteBean.class, partitionComponents,
				EACH_QUORUM);

		assertThat(actual.getEntity()).isNull();
		assertThat(actual.getPrimaryKey()).isSameAs(primaryKey);
		assertThat(actual.getEntityClass()).isSameAs((Class) CompleteBean.class);
		assertThat(actual.getEntityMeta()).isSameAs(meta);
		assertThat(actual.getIdMeta()).isSameAs(idMeta);
		assertThat(actual.getTtt().isPresent()).isFalse();
	}
}
