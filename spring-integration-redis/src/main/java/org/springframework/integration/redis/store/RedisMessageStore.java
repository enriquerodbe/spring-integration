/*
 * Copyright 2007-2013 the original author or authors
 *
 *     Licensed under the Apache License, Version 2.0 (the "License");
 *     you may not use this file except in compliance with the License.
 *     You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *     Unless required by applicable law or agreed to in writing, software
 *     distributed under the License is distributed on an "AS IS" BASIS,
 *     WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *     See the License for the specific language governing permissions and
 *     limitations under the License.
 */

package org.springframework.integration.redis.store;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.integration.store.*;
import org.springframework.messaging.Message;

import java.util.*;

import static java.lang.System.currentTimeMillis;

/**
 * Redis implementation of the key/value style {@link MessageStore} and {@link MessageGroupStore}
 *
 * @author Oleg Zhurakousky
 * @author Gary Russell
 * @since 2.1
 */
public class RedisMessageStore extends AbstractMessageGroupStore {

    private final static String MESSAGE_KEY_PREFIX = "MESSAGE_";
    private final static String LAST_RELEASED_SEQ_NUM_KEY = "LAST_RELEASED_SEQ_NUM";
    private final static String COMPLETE_KEY = "COMPLETE";
    private final static String LAST_MODIFIED_KEY = "LAST_MODIFIED";
    private final static String TIMESTAMP_KEY = "TIMESTAMP";

    private final RedisTemplate<Object, Object> redisTemplate;

    public RedisMessageStore(RedisTemplate<Object, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }


    @Override
    public MessageGroup removeMessageFromGroup(Object groupId, Message<?> messageToRemove) {
        redisTemplate.opsForHash().delete(groupId, makeMessageKey(messageToRemove));
        modify(groupId);
        return getMessageGroup(groupId);
    }

    @Override
    public void setLastReleasedSequenceNumberForGroup(Object groupId, int sequenceNumber) {
        modify(groupId, LAST_RELEASED_SEQ_NUM_KEY, sequenceNumber);
    }

    @Override
    public Iterator<MessageGroup> iterator() {
        Set<Object> groupIds = redisTemplate.keys("*");
        return new MessageGroupIterator(groupIds.iterator());
    }

    @Override
    public void completeGroup(Object groupId) {
        modify(groupId, COMPLETE_KEY, true);
    }

    @Override
    public int messageGroupSize(Object groupId) {
        int count = 0;
        for (Map.Entry<Object, Object> entry : redisTemplate.opsForHash().entries(groupId).entrySet()) {
            if (((String) entry.getKey()).startsWith(MESSAGE_KEY_PREFIX)) {
                count++;
            }
        }
        return count;
    }

    @Override
    public MessageGroup getMessageGroup(Object groupId) {
        return createMessageGroup(groupId, redisTemplate.opsForHash().entries(groupId));
    }

    private MessageGroup createMessageGroup(Object groupId, Map<Object, Object> entries) {
        Long valueTimestamp = (Long) entries.get(TIMESTAMP_KEY);
        Boolean valueComplete = (Boolean) entries.get(COMPLETE_KEY);
        Integer valueLastReleasedSeqNum = (Integer) entries.get(LAST_RELEASED_SEQ_NUM_KEY);
        Long valueLastModified = (Long) entries.get(LAST_MODIFIED_KEY);

        Collection<? extends Message<?>> messages = getMessagesFromGroup(entries);
        long timestamp = valueTimestamp != null ? valueTimestamp : 0;
        boolean complete = valueComplete != null ? valueComplete : false;
        int lastReleasedSeqNum = valueLastReleasedSeqNum != null ? valueLastReleasedSeqNum : 0;
        long lastModified = valueLastModified != null ? valueLastModified : 0;

        SimpleMessageGroup messageGroup = new SimpleMessageGroup(messages, groupId, timestamp, complete);
        messageGroup.setLastReleasedMessageSequenceNumber(lastReleasedSeqNum);
        messageGroup.setLastModified(lastModified);

        return messageGroup;
    }

    private Collection<? extends Message<?>> getMessagesFromGroup(Map<Object, Object> entries) {
        List<Message<?>> result = new ArrayList<Message<?>>(entries.size());
        for (Map.Entry<Object, Object> entry : entries.entrySet()) {
            String key = (String) entry.getKey();
            if (key.startsWith(MESSAGE_KEY_PREFIX)) {
                result.add((Message<?>) entry.getValue());
            }
        }
        return result;
    }

    @Override
    public MessageGroup addMessageToGroup(Object groupId, Message<?> message) {
        modify(groupId, makeMessageKey(message), message);
        redisTemplate.opsForHash().putIfAbsent(groupId, TIMESTAMP_KEY, currentTimeMillis());
        return getMessageGroup(groupId);
    }

    @Override
    public Message<?> pollMessageFromGroup(Object groupId) {
        Message<?> message = getMessageGroup(groupId).getOne();
        redisTemplate.opsForHash().delete(groupId, makeMessageKey(message));
        modify(groupId);
        return message;
    }

    @Override
    public void removeMessageGroup(Object groupId) {
        redisTemplate.opsForHash().delete(groupId, redisTemplate.opsForHash().keys(groupId).toArray());
    }


    private void modify(Object groupId, Object key, Object value) {
        Map<Object, Object> modification = new HashMap<Object, Object>(4);
        modification.put(key, value);
        modification.put(LAST_MODIFIED_KEY, currentTimeMillis());
        redisTemplate.opsForHash().putAll(groupId, modification);
    }

    private void modify(Object groupId) {
        redisTemplate.opsForHash().put(groupId, LAST_MODIFIED_KEY, currentTimeMillis());
    }

    private String makeMessageKey(Message<?> message) {
        return MESSAGE_KEY_PREFIX + message.getHeaders().getId();
    }


    private class MessageGroupIterator implements Iterator<MessageGroup> {

        private final Iterator<?> idIterator;

        private MessageGroupIterator(Iterator<?> idIterator) {
            this.idIterator = idIterator;
        }

        @Override
        public boolean hasNext() {
            return idIterator.hasNext();
        }

        @Override
        public MessageGroup next() {
            return getMessageGroup(idIterator.next());
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }
    }
}
