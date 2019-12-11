/*
 * (C) Copyright IBM Corp. 2019
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package com.ibm.fhir.persistence.jdbc.search.test;

import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotEquals;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertNull;
import static org.testng.AssertJUnit.assertTrue;

import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.LogManager;

import org.testng.annotations.AfterClass;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import com.ibm.fhir.config.FHIRConfiguration;
import com.ibm.fhir.config.FHIRRequestContext;
import com.ibm.fhir.model.resource.Location;
import com.ibm.fhir.model.resource.Resource;
import com.ibm.fhir.model.test.TestUtil;
import com.ibm.fhir.persistence.FHIRPersistence;
import com.ibm.fhir.persistence.MultiResourceResult;
import com.ibm.fhir.persistence.SingleResourceResult;
import com.ibm.fhir.persistence.context.FHIRPersistenceContext;
import com.ibm.fhir.persistence.context.FHIRPersistenceContextFactory;
import com.ibm.fhir.persistence.exception.FHIRPersistenceException;
import com.ibm.fhir.persistence.jdbc.impl.FHIRPersistenceJDBCImpl;
import com.ibm.fhir.persistence.jdbc.test.util.DerbyInitializer;
import com.ibm.fhir.search.context.FHIRSearchContext;
import com.ibm.fhir.search.util.SearchUtil;

/**
 * <a href="https://www.hl7.org/fhir/r4/location.html#positional">FHIR
 * Specification: Positional Searching for Location Resource</a>
 * <br>
 * Original LONG/LAT
 * 
 * <pre>
 *  "position": {
 *      "longitude": -83.6945691,
 *      "latitude": 42.25475478,
 *      "altitude": 0
 * }
 * </pre>
 */
public class JDBCSearchNearTest {
    private Properties testProps;

    protected Location savedResource;

    protected static FHIRPersistence persistence = null;

    @BeforeClass
    public void startup() throws Exception {
        InputStream inputStream = JDBCSearchNearTest.class.getResourceAsStream("/near.unitTest.properties");
        LogManager.getLogManager().readConfiguration(inputStream);

        FHIRConfiguration.setConfigHome("../fhir-persistence/target/test-classes");
        FHIRRequestContext.get().setTenantId("default");

        testProps = TestUtil.readTestProperties("test.jdbc.properties");

        DerbyInitializer derbyInit;
        String dbDriverName = this.testProps.getProperty("dbDriverName");
        if (dbDriverName != null && dbDriverName.contains("derby")) {
            derbyInit = new DerbyInitializer(this.testProps);
            derbyInit.bootstrapDb();
        }

        savedResource = TestUtil.readExampleResource("json/spec/location-example.json");

        persistence   = new FHIRPersistenceJDBCImpl(this.testProps);

        SingleResourceResult<Location> result =
                persistence.create(FHIRPersistenceContextFactory.createPersistenceContext(null), savedResource);
        assertTrue(result.isSuccess());

    }

    public MultiResourceResult<Resource> runQueryTest(String searchParamCode, String queryValue) throws Exception {
        Map<String, List<String>> queryParms = new HashMap<String, List<String>>(1);
        if (searchParamCode != null && queryValue != null) {
            queryParms.put(searchParamCode, Collections.singletonList(queryValue));
        }

        FHIRSearchContext ctx = SearchUtil.parseQueryParameters(Location.class, queryParms, true);
        FHIRPersistenceContext persistenceContext = FHIRPersistenceContextFactory.createPersistenceContext(null, ctx);
        MultiResourceResult<Resource> result = persistence.search(persistenceContext, Location.class);
        return result;
    }

    @AfterClass
    public void teardown() throws Exception {
        FHIRRequestContext.get().setTenantId("default");
    }

    @BeforeMethod(alwaysRun = true)
    public void startTrx() throws Exception {
        if (persistence != null && persistence.isTransactional()) {
            persistence.getTransaction().begin();
        }
    }

    @AfterMethod(alwaysRun = true)
    public void commitTrx() throws Exception {
        if (persistence != null && persistence.isTransactional()) {
            persistence.getTransaction().commit();
        }
    }

    @Test
    public void testSearchPositionSearchExactSmallRangeMatch() throws Exception {
        // Should match the loaded resource with a real range
        String searchParamCode = "near";
        String queryValue = "42.25475478|-83.6945691|10.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertFalse(result.getResource().size() == 0);
        assertNull(result.getOutcome());
    }

    @Test
    public void testSearchPositionSearchExactLargeRangeMatch() throws Exception {
        // Should match the loaded resource with a real range
        String searchParamCode = "near";
        String queryValue = "42.25475478|0|10.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertNotEquals(result.getResource().size(), 0);
        assertNull(result.getOutcome());
    }

    @Test
    public void testSearchPositionSearchExactMatchWithinSmallRange() throws Exception {
        // Should match the loaded resource
        String searchParamCode = "near";
        String queryValue = "-83.0|42.0|500.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertFalse(result.getResource().size() == 0);
        assertNull(result.getOutcome());
    }

    @Test
    public void testSearchPositionSearchExactMatchNotMatchingRange() throws Exception {
        // Should not match (opposite the loaded resource)
        String searchParamCode = "near";
        String queryValue = "-83.0|42.0|1.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertTrue(result.getResource().size() == 0);
    }

    @Test
    public void testSearchPositionSearchExactMatchWithinRangeNot() throws Exception {
        // Difference to expected location is greater than 523.3km
        String searchParamCode = "near";
        String queryValue = "-79|40|523.3|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertTrue(result.getResource().size() == 0);
    }

    @Test
    public void testSearchPositionSearchExactMatchWithinRange() throws Exception {
        // 40, -79
        // Difference to expected location is 1046.6km
        String searchParamCode = "near";
        String queryValue = "40|-79|1046.6|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertTrue(result.getResource().size() == 0);
    }

    @Test
    public void testSearchPositionSearchExactMatch() throws Exception {
        // Should match the loaded resource
        String searchParamCode = "near";
        String queryValue = "-83.6945691|42.25475478|0.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertFalse(result.getResource().size() == 0);
        assertNull(result.getOutcome());
    }

    @Test
    public void testSearchPositionSearchExactMatchNotMatching() throws Exception {
        // Should not match (opposite the loaded resource)
        String searchParamCode = "near";
        String queryValue = "83.6945691|-42.25475478|0.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertTrue(result.getResource().size() == 0);
    }

    @Test
    public void testSearchPositionSearchExactMatchUnitMiles() throws Exception {
        // Should match the loaded resource
        String searchParamCode = "near";
        String queryValue = "-83.6945691|42.25475478|0.0|mi_us";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertFalse(result.getResource().size() == 0);
        assertNull(result.getOutcome());
    }

    @Test(expectedExceptions = { FHIRPersistenceException.class })
    public void testSearchPositionSearchBadPrefix() throws Exception {
        // Should not match (opposite the loaded resource)
        String searchParamCode = "near";
        String queryValue = "ap83.6945691|-42.25475478|0.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertTrue(result.getResource().size() == 0);
    }

    @Test(expectedExceptions = { FHIRPersistenceException.class })
    public void testSearchPositionSearchBadInputLon() throws Exception {
        // Bad Input - Latitude
        String searchParamCode = "near";
        String queryValue = "-42.25475478|FUDGESHOULDNOTMATCH|0.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertTrue(result.getResource().size() == 0);
    }

    @Test(expectedExceptions = { FHIRPersistenceException.class })
    public void testSearchPositionSearchBadInputLat() throws Exception {
        // Bad Input - Latitude
        String searchParamCode = "near";
        String queryValue = "FUDGESHOULDNOTMATCH|-42.25475478|0.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertTrue(result.getResource().size() == 0);
    }

    @Test(expectedExceptions = { FHIRPersistenceException.class })
    public void testSearchPositionSearchBadInputRadius() throws Exception {
        // Bad Input - Latitude
        String searchParamCode = "near";
        String queryValue = "-42.25475478|-42.25475478|FUDGESHOULDNOTMATCH|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertTrue(result.getResource().size() == 0);
    }

    @Test(expectedExceptions = { FHIRPersistenceException.class })
    public void testSearchPositionSearchBadInputUnit() throws Exception {
        // Bad Input - Latitude
        String searchParamCode = "near";
        String queryValue = "-42.25475478|-42.25475478|0.0|FUDGESHOULDNOTMATCH";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertTrue(result.getResource().size() == 0);
    }

    @Test
    public void testSearchPositionSearchExactMatchGoodPrefix() throws Exception {
        // Should match the loaded resource
        String searchParamCode = "near";
        String queryValue = "eq-83.6945691|42.25475478|0.0|km";

        MultiResourceResult<Resource> result = runQueryTest(searchParamCode, queryValue);
        assertNotNull(result);
        assertFalse(result.getResource().size() == 0);
        assertNull(result.getOutcome());
    }
}