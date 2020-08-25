package com.cerner.test.it;

import static org.junit.Assert.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.io.FileUtils;
import org.hibernate.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.cerner.test.it.common.BaseIntegrationTest;
import com.drajer.ecrapp.model.Eicr;
import com.drajer.sof.model.ClientDetails;
import com.drajer.sof.model.LaunchDetails;

import ca.uhn.fhir.rest.client.api.IGenericClient;

public class ITLaunchController extends BaseIntegrationTest {

	@Mock
	IGenericClient genericClient;

	ClassLoader classLoader = getClass().getClassLoader();

	static String testSaveLaunchData;
	static String launchDetailString;
	static int savedLaunchId;
	static int testLaunchDetailsId;

	static int savedClientId;
	static String clientDetailString;
	static String testSaveClientData;

	static String systemLaunchInputData;

	static int testClientDetailsId;

	static List<ClientDetails> deleteClientList = new ArrayList<>();

	static List<LaunchDetails> deleteLaunchList = new ArrayList<>();

	static Set<Integer> transactionalEntrySet = new HashSet<>(Arrays.asList(10, 13, 233, 191, 354));

	@Before
	public void launchTestSetUp() throws IOException {

		tx = session.beginTransaction();

		createTestClientDetailsInDB();
		getSystemLaunchInputData();

		createTestLaunchDetailsInDB();
		// getSaveLaunchInputData();
		session.flush();
		tx.commit();

	}

	@After
	public void cleanUp() {
		tx = session.beginTransaction();
		dataCleanup();

		tx.commit();

	}

	@Test
	public void testGetLaunchDetailsById() throws Exception {
		ResponseEntity<String> response = restTemplate.exchange(
				createURLWithPort("/api/launchDetails/" + testLaunchDetailsId), HttpMethod.GET, null, String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());

		LaunchDetails launchDetails = mapper.readValue(response.getBody(), LaunchDetails.class);

		assertEquals(mapper.readValue(launchDetailString, LaunchDetails.class).getClientId(),
				launchDetails.getClientId());

	}

	@Test
	public void testTriggerDataFromEHR() throws Exception {

		ResponseEntity<String> response = restTemplate.exchange(
				createURLWithPort("/api/triggerQueryService/" + testLaunchDetailsId), HttpMethod.GET, null,
				String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains("Success"));
		;

	}

	@Test
	public void testLoadDataFromEHR() throws Exception {
		ResponseEntity<String> response = restTemplate.exchange(
				createURLWithPort("/api/loadingQueryService/" + testLaunchDetailsId), HttpMethod.GET, null,
				String.class);
		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains("Success"));
		;

	}

	@Test
	public void testSystemLaunch() throws Exception {
		headers.setContentType(MediaType.APPLICATION_JSON);

		HttpEntity<String> entity = new HttpEntity<String>(systemLaunchInputData, headers);

		ResponseEntity<String> response = restTemplate.exchange(createURLWithPort("/api/systemLaunch"), HttpMethod.POST,
				entity, String.class);

		Thread.sleep(200000);

		Query query = session.createQuery("from Eicr order by id DESC");
		query.setMaxResults(1);
		Eicr last = (Eicr) query.uniqueResult();

		Document expectedDoc = getExpectedXml();
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document actualDoc = builder.parse(new InputSource(new StringReader(last.getData())));

		BufferedReader br1 = new BufferedReader(
				new FileReader(new File(classLoader.getResource("EICR_Expected").getFile())));
		BufferedReader br2 = new BufferedReader(new StringReader(last.getData()));

		assertEquals(HttpStatus.OK, response.getStatusCode());
		assertTrue(response.getBody().contains("App is launched successfully"));

		assertEquals(expectedDoc.getDocumentElement().getTextContent(),
				actualDoc.getDocumentElement().getTextContent());

		assertTrue(compareStringBuffer(br1, br2));

	}

	private String createURLWithPort(String uri) {
		return URL + port + uri;
	}

	private void getSystemLaunchInputData() throws IOException {
		File systemLaunchInputDataFile = new File(classLoader.getResource("systemLaunchRequest.json").getFile());
		systemLaunchInputData = FileUtils.readFileToString(systemLaunchInputDataFile, StandardCharsets.UTF_8);

	}

	private void createTestLaunchDetailsInDB() throws IOException {
		File dataEntryFile = new File(classLoader.getResource("saveLaunchDataEntry.json").getFile());
		launchDetailString = FileUtils.readFileToString(dataEntryFile, StandardCharsets.UTF_8);
		testLaunchDetailsId = (int) session.save(mapper.readValue(launchDetailString, LaunchDetails.class));

		LaunchDetails launchDetailsToBeDeleted = (LaunchDetails) session.get(LaunchDetails.class, testLaunchDetailsId);
		deleteLaunchList.add(launchDetailsToBeDeleted);

	}

	private void createTestClientDetailsInDB() throws IOException {

		File dataEntryFile = new File(classLoader.getResource("saveClientDataEntry1.json").getFile());
		clientDetailString = FileUtils.readFileToString(dataEntryFile, StandardCharsets.UTF_8);
		testClientDetailsId = (int) session.save(mapper.readValue(clientDetailString, ClientDetails.class));

		ClientDetails clientDetailsToBeDeleted = (ClientDetails) session.get(ClientDetails.class, testClientDetailsId);
		deleteClientList.add(clientDetailsToBeDeleted);
	}

	private void dataCleanup() {
		for (LaunchDetails launchDetails : deleteLaunchList) {
			session.load(LaunchDetails.class, launchDetails.getId());
			session.delete(launchDetails);
		}
		deleteLaunchList.clear();

		for (ClientDetails clientDetails : deleteClientList) {
			session.load(ClientDetails.class, clientDetails.getId());
			session.delete(clientDetails);
		}
		deleteClientList.clear();
	}

	private Document getExpectedXml() throws ParserConfigurationException, SAXException, IOException {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder = factory.newDocumentBuilder();
		Document document = builder.parse(new File(classLoader.getResource("EICR_Expected").getFile()));
		return document;

	}

	private boolean compareStringBuffer(BufferedReader br1, BufferedReader br2) throws IOException {
		boolean isSame = false;
		String sCurrentLine;
		List<String> list1 = new ArrayList<String>();
		List<String> list2 = new ArrayList<String>();

		int count = 0;

		while ((sCurrentLine = br1.readLine()) != null) {
			list1.add(sCurrentLine);
		}
		while ((sCurrentLine = br2.readLine()) != null) {
			list2.add(sCurrentLine);
		}

		if (list1.size() != list2.size())
			return false;

		for (int i = 0; i < list1.size(); i++) {
			if (!transactionalEntrySet.contains(i))
				if (list1.get(i).equals(list2.get(i))) {
					// System.out.println(list1.get(i));
				} else {
					count++;
				}

		}
		if (count == 0)
			isSame = true;
		return isSame;
	}

}