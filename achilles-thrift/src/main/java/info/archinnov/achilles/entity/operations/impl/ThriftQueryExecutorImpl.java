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
package info.archinnov.achilles.entity.operations.impl;

import info.archinnov.achilles.composite.ThriftCompositeFactory;
import info.archinnov.achilles.context.ThriftPersistenceContext;
import info.archinnov.achilles.context.execution.SafeExecutionContext;
import info.archinnov.achilles.dao.ThriftGenericWideRowDao;
import info.archinnov.achilles.entity.metadata.EntityMeta;
import info.archinnov.achilles.entity.metadata.PropertyMeta;
import info.archinnov.achilles.iterator.ThriftCounterSliceIterator;
import info.archinnov.achilles.iterator.ThriftSliceIterator;
import info.archinnov.achilles.query.SliceQuery;
import info.archinnov.achilles.type.ConsistencyLevel;

import java.util.List;

import me.prettyprint.hector.api.beans.Composite;
import me.prettyprint.hector.api.beans.HColumn;
import me.prettyprint.hector.api.beans.HCounterColumn;
import me.prettyprint.hector.api.mutation.Mutator;

public class ThriftQueryExecutorImpl {
	private ThriftCompositeFactory compositeFactory = new ThriftCompositeFactory();

	public <T> List<HColumn<Composite, Object>> findColumns(final SliceQuery<T> query, ThriftPersistenceContext context) {
		EntityMeta meta = query.getMeta();
		final ThriftGenericWideRowDao wideRowDao = context.getWideRowDao();

		PropertyMeta idMeta = meta.getIdMeta();

		final Composite[] composites = compositeFactory.createForClusteredQuery(idMeta, query.getClusteringsFrom(),
				query.getClusteringsTo(), query.getBounding(), query.getOrdering());
		final Object rowKey = compositeFactory.buildRowKey(context);

		return context.executeWithReadConsistencyLevel(new SafeExecutionContext<List<HColumn<Composite, Object>>>() {
			@Override
			public List<HColumn<Composite, Object>> execute() {
				return wideRowDao.findRawColumnsRange(rowKey, composites[0], composites[1], query.getLimit(), query
						.getOrdering().isReverse());
			}
		}, query.getConsistencyLevel());
	}

	public <T> ThriftSliceIterator<Object, Object> getColumnsIterator(final SliceQuery<T> query,
			ThriftPersistenceContext context) {
		EntityMeta meta = query.getMeta();
		final ThriftGenericWideRowDao wideRowDao = context.getWideRowDao();
		PropertyMeta idMeta = meta.getIdMeta();
		final Object rowKey = compositeFactory.buildRowKey(context);

		final Composite[] composites = compositeFactory.createForClusteredQuery(idMeta, query.getClusteringsFrom(),
				query.getClusteringsTo(), query.getBounding(), query.getOrdering());

		return context.executeWithReadConsistencyLevel(new SafeExecutionContext<ThriftSliceIterator<Object, Object>>() {
			@Override
			public ThriftSliceIterator<Object, Object> execute() {
				return wideRowDao.getColumnsIterator(rowKey, composites[0], composites[1], query.getOrdering()
						.isReverse(), query.getBatchSize());
			}
		}, query.getConsistencyLevel());
	}

	public void removeColumns(List<HColumn<Composite, Object>> columns, final ConsistencyLevel consistencyLevel,
			final ThriftPersistenceContext context) {
		final Object rowKey = compositeFactory.buildRowKey(context);

		final ThriftGenericWideRowDao wideRowDao = context.getWideRowDao();
		final Mutator<Object> mutator = wideRowDao.buildMutator();
		for (HColumn<Composite, Object> column : columns) {
			wideRowDao.removeColumnBatch(rowKey, column.getName(), mutator);
		}

		context.executeWithWriteConsistencyLevel(new SafeExecutionContext<Void>() {
			@Override
			public Void execute() {
				wideRowDao.executeMutator(mutator);
				return null;
			}
		}, consistencyLevel);

	}

	public <T> List<HCounterColumn<Composite>> findCounterColumns(final SliceQuery<T> query,
			ThriftPersistenceContext context) {
		EntityMeta meta = query.getMeta();
		final ThriftGenericWideRowDao wideRowDao = context.getWideRowDao();
		PropertyMeta idMeta = meta.getIdMeta();
		final Object rowKey = compositeFactory.buildRowKey(context);

		final Composite[] composites = compositeFactory.createForClusteredQuery(idMeta, query.getClusteringsFrom(),
				query.getClusteringsTo(), query.getBounding(), query.getOrdering());

		return context.executeWithReadConsistencyLevel(new SafeExecutionContext<List<HCounterColumn<Composite>>>() {
			@Override
			public List<HCounterColumn<Composite>> execute() {
				return wideRowDao.findCounterColumnsRange(rowKey, composites[0], composites[1], query.getLimit(), query
						.getOrdering().isReverse());
			}
		}, query.getConsistencyLevel());
	}

	public <T> ThriftCounterSliceIterator<Object> getCounterColumnsIterator(final SliceQuery<T> query,
			ThriftPersistenceContext context) {
		EntityMeta meta = query.getMeta();
		final ThriftGenericWideRowDao wideRowDao = context.getWideRowDao();
		final Object rowKey = compositeFactory.buildRowKey(context);
		PropertyMeta idMeta = meta.getIdMeta();

		final Composite[] composites = compositeFactory.createForClusteredQuery(idMeta, query.getClusteringsFrom(),
				query.getClusteringsTo(), query.getBounding(), query.getOrdering());

		return context.executeWithReadConsistencyLevel(new SafeExecutionContext<ThriftCounterSliceIterator<Object>>() {
			@Override
			public ThriftCounterSliceIterator<Object> execute() {
				return wideRowDao.getCounterColumnsIterator(rowKey, composites[0], composites[1], query.getOrdering()
						.isReverse(), query.getBatchSize());
			}
		}, query.getConsistencyLevel());
	}

	public void removeCounterColumns(List<HCounterColumn<Composite>> counterColumns,
			final ConsistencyLevel consistencyLevel, final ThriftPersistenceContext context) {
		final Object rowKey = compositeFactory.buildRowKey(context);
		final ThriftGenericWideRowDao wideRowDao = context.getWideRowDao();
		final Mutator<Object> mutator = wideRowDao.buildMutator();
		for (HCounterColumn<Composite> counterColumn : counterColumns) {
			wideRowDao.removeCounterBatch(rowKey, counterColumn.getName(), mutator);
		}

		context.executeWithWriteConsistencyLevel(new SafeExecutionContext<Void>() {
			@Override
			public Void execute() {
				wideRowDao.executeMutator(mutator);
				return null;
			}
		}, consistencyLevel);

	}

	public void removeRow(final Object partitionKey, ThriftPersistenceContext context, ConsistencyLevel consistencyLevel) {
		final ThriftGenericWideRowDao wideRowDao = context.getWideRowDao();
		final Mutator<Object> mutator = wideRowDao.buildMutator();
		final Object rowKey = compositeFactory.buildRowKey(context);
		wideRowDao.removeRowBatch(rowKey, mutator);
		context.executeWithWriteConsistencyLevel(new SafeExecutionContext<Void>() {
			@Override
			public Void execute() {
				wideRowDao.executeMutator(mutator);
				return null;
			}
		}, consistencyLevel);
	}

}
