package org.tdar.core.bean.notification.aws;

import static org.junit.Assert.*;

import java.util.Arrays;

import org.junit.Before;
import org.junit.Test;
import org.tdar.core.bean.AbstractIntegrationTestCase;
import org.tdar.core.bean.notification.EmailType;
import org.tdar.utils.MessageHelper;


public class TestAwsMessageTest {
	
	TestAwsMessage message;
	
	@Before 
	public void setUp(){
		message =  new TestAwsMessage();
	}
	
	@Test
	public void testEmailType() {
		assertTrue(message.getEmailType()==null);
		message.setEmailType(EmailType.TEST_EMAIL);
		assertEquals(message.getEmailType(),EmailType.TEST_EMAIL);
	}
	
	@Test
	public void testEmailSubject(){
		assertNotNull(message.createSubjectLine());
	}
	
	@Test
	public void testMessageNames(){
		assertNull(message.getEmailType());
		message.setEmailType(EmailType.TEST_EMAIL);

		String firstName = "John";
		String lastName  = "Doe";
		
		assertNull(message.getMap().get("firstName"));
		assertNull(message.getMap().get("lastName"));
		
		message.addData("firstName", firstName);
		message.addData("lastName", lastName);
		
		assertNotNull(message.getMap().get("firstName"));
		assertNotNull(message.getMap().get("lastName"));
		
		String subject = MessageHelper.getInstance().getText("EmailType.TEST_EMAIL",Arrays.asList(lastName, firstName));
		
		System.out.printf("Message is : %s",subject);
		assertEquals(message.createSubjectLine(), subject);
	}
}
