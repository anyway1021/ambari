/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ambari.server.upgrade;

import static org.easymock.EasyMock.anyObject;
import static org.easymock.EasyMock.anyString;
import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.createMockBuilder;
import static org.easymock.EasyMock.eq;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.reset;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.lang.reflect.Method;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.persistence.Cache;
import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.EntityTransaction;

import org.apache.ambari.server.actionmanager.ActionManager;
import org.apache.ambari.server.configuration.Configuration;
import org.apache.ambari.server.controller.AmbariManagementController;
import org.apache.ambari.server.controller.AmbariManagementControllerImpl;
import org.apache.ambari.server.controller.MaintenanceStateHelper;
import org.apache.ambari.server.orm.DBAccessor;
import org.apache.ambari.server.state.Cluster;
import org.apache.ambari.server.state.Clusters;
import org.apache.ambari.server.state.Config;
import org.apache.ambari.server.state.stack.OsFamily;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMock;
import org.easymock.EasyMockRunner;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.easymock.MockType;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.inject.Binder;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.Provider;

@RunWith(EasyMockRunner.class)
public class UpgradeCatalog300Test {

  @Mock(type = MockType.STRICT)
  private Provider<EntityManager> entityManagerProvider;

  @Mock(type = MockType.NICE)
  private Injector injector;

  @Mock(type = MockType.NICE)
  private EntityManager entityManager;

  @Mock(type = MockType.NICE)
  private DBAccessor dbAccessor;

  @Mock(type = MockType.NICE)
  private OsFamily osFamily;

  @Mock(type = MockType.NICE)
  private Configuration configuration;

  @Mock(type = MockType.NICE)
  private Config config;

  @Mock(type = MockType.NICE)
  private ActionManager actionManager;

  @Mock(type = MockType.NICE)
  private Clusters clusters;

  @Mock(type = MockType.NICE)
  private Cluster cluster;

  @Before
  public void init() {
    reset(entityManagerProvider, injector);

    expect(entityManagerProvider.get()).andReturn(entityManager).anyTimes();

    expect(injector.getInstance(Gson.class)).andReturn(null).anyTimes();
    expect(injector.getInstance(MaintenanceStateHelper.class)).andReturn(null).anyTimes();

    replay(entityManagerProvider, injector);
  }

  @After
  public void tearDown() {
  }

  @Test
  public void testExecuteDMLUpdates() throws Exception {
    Method addNewConfigurationsFromXml = AbstractUpgradeCatalog.class.getDeclaredMethod("addNewConfigurationsFromXml");
    Method showHcatDeletedUserMessage = UpgradeCatalog300.class.getDeclaredMethod("showHcatDeletedUserMessage");
    Method setStatusOfStagesAndRequests = UpgradeCatalog300.class.getDeclaredMethod("setStatusOfStagesAndRequests");
    Method updateLogSearchConfigs = UpgradeCatalog300.class.getDeclaredMethod("updateLogSearchConfigs");

   UpgradeCatalog300 upgradeCatalog300 = createMockBuilder(UpgradeCatalog300.class)
            .addMockedMethod(showHcatDeletedUserMessage)
            .addMockedMethod(addNewConfigurationsFromXml)
            .addMockedMethod(setStatusOfStagesAndRequests)
            .addMockedMethod(updateLogSearchConfigs)
            .createMock();


    upgradeCatalog300.addNewConfigurationsFromXml();
    upgradeCatalog300.showHcatDeletedUserMessage();
    upgradeCatalog300.setStatusOfStagesAndRequests();

    upgradeCatalog300.updateLogSearchConfigs();
    expectLastCall().once();

    replay(upgradeCatalog300);

    upgradeCatalog300.executeDMLUpdates();

    verify(upgradeCatalog300);
  }

  @Test
  public void testExecuteDDLUpdates() throws Exception {
    Module module = new Module() {
      @Override
      public void configure(Binder binder) {
        binder.bind(DBAccessor.class).toInstance(dbAccessor);
        binder.bind(OsFamily.class).toInstance(osFamily);
        binder.bind(EntityManager.class).toInstance(entityManager);
        binder.bind(Configuration.class).toInstance(configuration);
      }
    };

    Capture<DBAccessor.DBColumnInfo> clusterConfigSelectedColumn = newCapture();
    Capture<DBAccessor.DBColumnInfo> clusterConfigSelectedTimestampColumn = newCapture();
    dbAccessor.addColumn(eq(UpgradeCatalog300.CLUSTER_CONFIG_TABLE), capture(clusterConfigSelectedColumn));
    dbAccessor.addColumn(eq(UpgradeCatalog300.CLUSTER_CONFIG_TABLE), capture(clusterConfigSelectedTimestampColumn));

    replay(dbAccessor, configuration);

    Injector injector = Guice.createInjector(module);
    UpgradeCatalog300 upgradeCatalog300 = injector.getInstance(UpgradeCatalog300.class);
    upgradeCatalog300.executeDDLUpdates();

    DBAccessor.DBColumnInfo capturedSelectedColumn = clusterConfigSelectedColumn.getValue();
    Assert.assertNotNull(capturedSelectedColumn);
    Assert.assertEquals(UpgradeCatalog300.CLUSTER_CONFIG_SELECTED_COLUMN, capturedSelectedColumn.getName());
    Assert.assertEquals(Short.class, capturedSelectedColumn.getType());

    DBAccessor.DBColumnInfo capturedSelectedTimestampColumn = clusterConfigSelectedTimestampColumn.getValue();
    Assert.assertNotNull(capturedSelectedTimestampColumn);
    Assert.assertEquals(UpgradeCatalog300.CLUSTER_CONFIG_SELECTED_TIMESTAMP_COLUMN, capturedSelectedTimestampColumn.getName());
    Assert.assertEquals(Long.class, capturedSelectedTimestampColumn.getType());

    verify(dbAccessor);
  }

  /**
   * Tests pre-DML executions.
   *
   * @throws Exception
   */
  @Test
  public void testExecutePreDMLUpdates() throws Exception {
    Module module = new Module() {
      @Override
      public void configure(Binder binder) {
        binder.bind(DBAccessor.class).toInstance(dbAccessor);
        binder.bind(OsFamily.class).toInstance(osFamily);
        binder.bind(EntityManager.class).toInstance(entityManager);
        binder.bind(Configuration.class).toInstance(configuration);
      }
    };

    EntityManagerFactory emFactory = EasyMock.createNiceMock(EntityManagerFactory.class);
    Cache emCache = EasyMock.createNiceMock(Cache.class);

    expect(entityManager.getEntityManagerFactory()).andReturn(emFactory).atLeastOnce();
    expect(emFactory.getCache()).andReturn(emCache).atLeastOnce();

    EntityTransaction mockTransaction = EasyMock.createNiceMock(EntityTransaction.class);
    Connection mockConnection = EasyMock.createNiceMock(Connection.class);
    Statement mockStatement = EasyMock.createNiceMock(Statement.class);

    expect(dbAccessor.getConnection()).andReturn(mockConnection).once();
    expect(mockConnection.createStatement()).andReturn(mockStatement).once();

    expect(mockStatement.executeQuery(EasyMock.anyString())).andReturn(
        EasyMock.createNiceMock(ResultSet.class));

    expect(entityManager.getTransaction()).andReturn(
        mockTransaction).atLeastOnce();

    dbAccessor.dropTable(UpgradeCatalog300.CLUSTER_CONFIG_MAPPING_TABLE);
    EasyMock.expectLastCall().once();

    replay(dbAccessor, entityManager, emFactory, emCache, mockConnection, mockTransaction,
        mockStatement, configuration);

    Injector injector = Guice.createInjector(module);
    UpgradeCatalog300 upgradeCatalog300 = injector.getInstance(UpgradeCatalog300.class);
    upgradeCatalog300.executePreDMLUpdates();

    verify(dbAccessor, entityManager, emFactory, emCache);
  }
  
  @Test
  public void testLogSearchUpdateConfigs() throws Exception {
    reset(clusters, cluster);
    expect(clusters.getClusters()).andReturn(ImmutableMap.of("normal", cluster)).once();

    EasyMockSupport easyMockSupport = new EasyMockSupport();

    Injector injector2 = easyMockSupport.createNiceMock(Injector.class);
    AmbariManagementControllerImpl controller = createMockBuilder(AmbariManagementControllerImpl.class)
        .addMockedMethod("createConfiguration")
        .addMockedMethod("getClusters", new Class[]{})
        .addMockedMethod("createConfig")
        .withConstructor(actionManager, clusters, injector)
        .createNiceMock();

    expect(injector2.getInstance(AmbariManagementController.class)).andReturn(controller).anyTimes();
    expect(controller.getClusters()).andReturn(clusters).anyTimes();

    Config confSomethingElse1 = easyMockSupport.createNiceMock(Config.class);
    expect(confSomethingElse1.getType()).andReturn("something-else-1");
    Config confSomethingElse2 = easyMockSupport.createNiceMock(Config.class);
    expect(confSomethingElse2.getType()).andReturn("something-else-2");
    Config confLogSearchConf1 = easyMockSupport.createNiceMock(Config.class);
    expect(confLogSearchConf1.getType()).andReturn("service-1-logsearch-conf");
    Config confLogSearchConf2 = easyMockSupport.createNiceMock(Config.class);
    expect(confLogSearchConf2.getType()).andReturn("service-2-logsearch-conf");
    
    Map<String, String> oldLogSearchConf = ImmutableMap.of(
        "service_name", "Service",
        "component_mappings", "Component Mappings",
        "content", "Content");

    Collection<Config> configs = Arrays.asList(confSomethingElse1, confLogSearchConf1, confSomethingElse2, confLogSearchConf2);
    
    expect(cluster.getAllConfigs()).andReturn(configs).atLeastOnce();
    expect(cluster.getDesiredConfigByType("service-1-logsearch-conf")).andReturn(confLogSearchConf1).once();
    expect(cluster.getDesiredConfigByType("service-2-logsearch-conf")).andReturn(confLogSearchConf2).once();
    expect(confLogSearchConf1.getProperties()).andReturn(oldLogSearchConf).once();
    expect(confLogSearchConf2.getProperties()).andReturn(oldLogSearchConf).once();
    Capture<Map<String, String>> logSearchConfCapture = EasyMock.newCapture(CaptureType.ALL);
    expect(controller.createConfig(anyObject(Cluster.class), anyString(), capture(logSearchConfCapture), anyString(),
        EasyMock.<Map<String, Map<String, String>>>anyObject())).andReturn(config).times(2);

    replay(clusters, cluster);
    replay(controller, injector2);
    replay(confSomethingElse1, confSomethingElse2, confLogSearchConf1, confLogSearchConf2);
    new UpgradeCatalog300(injector2).updateLogSearchConfigs();
    easyMockSupport.verifyAll();

    List<Map<String, String>> updatedLogSearchConfs = logSearchConfCapture.getValues();
    assertEquals(updatedLogSearchConfs.size(), 2);
    for (Map<String, String> updatedLogSearchConf : updatedLogSearchConfs) {
      assertTrue(Maps.difference(Collections.<String, String> emptyMap(), updatedLogSearchConf).areEqual());
    }
  }
}
