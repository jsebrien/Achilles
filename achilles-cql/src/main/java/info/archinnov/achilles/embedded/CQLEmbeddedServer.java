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
package info.archinnov.achilles.embedded;

import static info.archinnov.achilles.configuration.CQLConfigurationParameters.*;
import static info.archinnov.achilles.configuration.ConfigurationParameters.*;
import static info.archinnov.achilles.context.CQLDaoContext.*;
import info.archinnov.achilles.entity.manager.CQLPersistenceManager;
import info.archinnov.achilles.entity.manager.CQLPersistenceManagerFactory;

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;

import org.apache.cassandra.thrift.Cassandra;
import org.apache.cassandra.thrift.Compression;
import org.apache.cassandra.thrift.ConsistencyLevel;
import org.apache.cassandra.thrift.CqlResult;
import org.apache.cassandra.thrift.TBinaryProtocol;
import org.apache.commons.lang.StringUtils;
import org.apache.thrift.protocol.TProtocol;
import org.apache.thrift.transport.TFramedTransport;
import org.apache.thrift.transport.TSocket;
import org.apache.thrift.transport.TTransport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.datastax.driver.core.Session;
import com.datastax.driver.core.SimpleStatement;

public class CQLEmbeddedServer extends AchillesEmbeddedServer {
	private static final Object SEMAPHORE = new Object();
	private static final Logger LOGGER = LoggerFactory.getLogger(CQLEmbeddedServer.class);
	private static final Logger DML_LOGGER = LoggerFactory.getLogger(ACHILLES_DML_STATEMENT);

	private static String entityPackages;
	private static boolean initialized = false;

	private static Session session;
	private static CQLPersistenceManagerFactory pmf;
	private static CQLPersistenceManager manager;

	public CQLEmbeddedServer(boolean cleanCassandraDataFile, String entityPackages, String keyspaceName) {
		if (StringUtils.isEmpty(entityPackages))
			throw new IllegalArgumentException("Entity packages should be provided");

		synchronized (SEMAPHORE) {
			if (!initialized) {
				startServer(cleanCassandraDataFile);
				CQLEmbeddedServer.entityPackages = entityPackages;
				initialize(keyspaceName);
			}
		}
	}

	private void initialize(String keyspaceName) {

		Map<String, Object> configMap = new HashMap<String, Object>();

		String cassandraHost = System.getProperty(CASSANDRA_HOST);
		if (StringUtils.isNotBlank(cassandraHost) && cassandraHost.contains(":")) {
			String[] split = cassandraHost.split(":");
			configMap.put(CONNECTION_CONTACT_POINTS_PARAM, split[0]);
			configMap.put(CONNECTION_PORT_PARAM, Integer.parseInt(split[1]));
		} else {
			createAchillesKeyspace(keyspaceName);
			configMap.put(CONNECTION_CONTACT_POINTS_PARAM, CASSANDRA_TEST_HOST);
			configMap.put(CONNECTION_PORT_PARAM, CASSANDRA_CQL_TEST_PORT);
		}

		configMap.put(ENTITY_PACKAGES_PARAM, entityPackages);
		configMap.put(KEYSPACE_NAME_PARAM, CASSANDRA_TEST_KEYSPACE_NAME);
		configMap.put(FORCE_CF_CREATION_PARAM, true);

		pmf = new CQLPersistenceManagerFactory(configMap);
		manager = pmf.createPersistenceManager();
		session = manager.getNativeSession();
		initialized = true;
	}

	public int getCqlPort() {
		return CASSANDRA_CQL_TEST_PORT;
	}

	public CQLPersistenceManagerFactory getPersistenceManagerFactory() {
		return pmf;
	}

	public CQLPersistenceManager getPersistenceManager() {
		return manager;
	}

	private void createAchillesKeyspace(String keyspaceName) {

		TTransport tr = new TFramedTransport(new TSocket("localhost", CASSANDRA_THRIFT_TEST_PORT));
		TProtocol proto = new TBinaryProtocol(tr, true, true);
		Cassandra.Client client = new Cassandra.Client(proto);
		try {
			tr.open();

			String checkKeyspace = "SELECT keyspace_name from system.schema_keyspaces WHERE keyspace_name='"
					+ keyspaceName + "'";
			CqlResult cqlResult = client.execute_cql3_query(ByteBuffer.wrap(checkKeyspace.getBytes()),
					Compression.NONE, ConsistencyLevel.ONE);

			if (cqlResult.getRowsSize() == 0) {
				LOGGER.info("Create keyspace " + keyspaceName);

				String cql = "CREATE keyspace "
						+ keyspaceName
						+ " WITH REPLICATION = {'class':'SimpleStrategy', 'replication_factor':1} AND DURABLE_WRITES=false";

				client.execute_cql3_query(ByteBuffer.wrap(cql.getBytes()), Compression.NONE, ConsistencyLevel.ONE);
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			tr.close();
		}
	}

	public void truncateTable(String tableName) {
		String query = "TRUNCATE " + tableName;
		session.execute(new SimpleStatement(query).setConsistencyLevel(com.datastax.driver.core.ConsistencyLevel.ALL));
		DML_LOGGER.debug("{} : [{}] with CONSISTENCY LEVEL [{}]", "Simple query", query, "ALL");
	}
}
