/*
 * Copyright 2002-2008 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.integration.transformer;

import static org.junit.Assert.assertEquals;

import java.util.Date;

import org.junit.Test;

import org.springframework.integration.core.Message;
import org.springframework.integration.core.MessagingException;
import org.springframework.integration.message.GenericMessage;
import org.springframework.integration.message.StringMessage;

/**
 * @author Mark Fisher
 */
public class PayloadTransformerTests {

	@Test
	public void testSuccessfulTransformation() {
		TestPayloadTransformer transformer = new TestPayloadTransformer();
		Message<?> message = new StringMessage("foo");
		Message<?> result = transformer.transform(message);
		assertEquals(3, result.getPayload());
	}

	@Test(expected=MessagingException.class)
	public void testExceptionThrownByTransformer() {
		TestPayloadTransformer transformer = new TestPayloadTransformer();
		Message<?> message = new StringMessage("bad");
		transformer.transform(message);
	}

	@Test(expected=MessagingException.class)
	public void testWrongPayloadType() {
		TestPayloadTransformer transformer = new TestPayloadTransformer();
		Message<?> message = new GenericMessage<Date>(new Date());
		transformer.transform(message);
	}


	private static class TestPayloadTransformer extends AbstractPayloadTransformer<String, Integer> {

		public Integer transformPayload(String s) throws Exception {
			if (s.equals("bad")) {
				throw new Exception("bad input!");
			}
			return s.length();
		}
	}

}