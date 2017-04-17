package com.ymatou.mq.rabbit.dispatcher.service;

import com.rabbitmq.client.*;
import com.ymatou.mq.infrastructure.model.CallbackMessage;
import com.ymatou.mq.infrastructure.model.Message;
import com.ymatou.mq.rabbit.RabbitChannelFactory;
import com.ymatou.mq.rabbit.config.RabbitConfig;
import com.ymatou.mq.rabbit.support.ChannelWrapper;
import com.ymatou.mq.rabbit.support.RabbitConstants;
import org.apache.commons.lang.SerializationUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * rabbit consumer
 * Created by zhangzhihua on 2017/4/1.
 */
public class MessageConsumer implements Consumer{

    private static final Logger logger = LoggerFactory.getLogger(MessageConsumer.class);

    /**
     * 应用id
     */
    private String appId;

    /**
     * 队列code
     */
    private String queueCode;

    /**
     * 回调url KEY
     */
    private String callbackKey;

    /**
     * master集群通道
     */
    private Channel masterChannel;

    /**
     * slave集群通道
     */
    private Channel slaveChannel;

    /**
     * rabbit配置
     */
    private RabbitConfig rabbitConfig;

    /**
     * dispatch回调服务
     */
    private DispatchCallbackService dispatchCallbackService;

    public MessageConsumer(String appId, String queueCode,String callbackKey){
        this.appId = appId;
        this.queueCode = queueCode;
        this.callbackKey = callbackKey;
    }

    /**
     * 启动消费监听
     */
    public void start(){
        try {
            //master channel
            //TODO 可调整conn/channel对应的数量关系
            ChannelWrapper masterChannelWrapper = RabbitChannelFactory.createChannelWrapper(RabbitConstants.CLUSTER_MASTER,rabbitConfig);
            masterChannel = masterChannelWrapper.getChannel();
            //TODO 处理channel关闭事件
            masterChannel.addShutdownListener(new ShutdownListener() {
                @Override
                public void shutdownCompleted(ShutdownSignalException cause) {
                    logger.error("shutdownCompleted,cause:" + cause);
                }
            });
            masterChannel.basicConsume(this.queueCode,false,this);

            //slave channel
            ChannelWrapper slaveChannelWrapper = RabbitChannelFactory.createChannelWrapper(RabbitConstants.CLUSTER_SLAVE,rabbitConfig);
            slaveChannel = slaveChannelWrapper.getChannel();
            slaveChannel.addShutdownListener(new ShutdownListener() {
                @Override
                public void shutdownCompleted(ShutdownSignalException cause) {
                    logger.error("shutdownCompleted,cause:" + cause);
                }
            });
            slaveChannel.basicConsume(this.queueCode,false,this);
        } catch (Exception e) {
            logger.error("basic consume error,queueCode:{}.",queueCode,e);
        }

    }

    /**
     * 关闭消费监听
     */
    public void stop(){
        //TODO
    }

    @Override
    public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
        logger.info("consumerTag:{},envelope:{},properties:{}.",consumerTag,envelope,properties);

        try {
            CallbackMessage callbackMessage = new CallbackMessage();
            callbackMessage.setAppId(appId);
            callbackMessage.setQueueCode(queueCode);
            callbackMessage.setCallbackKey(callbackKey);
            String msgId = properties.getMessageId();
            callbackMessage.setId(msgId);
            String bizId = properties.getCorrelationId();
            callbackMessage.setBizId(bizId);
            String sbody = (String) SerializationUtils.deserialize(body);
            callbackMessage.setBody(sbody);

            //MDC.put("logPrefix", "MessageConsumer|" + bizId);

            dispatchCallbackService.invoke(callbackMessage);
        } catch (Exception e) {
            logger.error("handleDelivery message error,consumerTag:{},envelope:{},properties:{}.",consumerTag,envelope,properties,e);
        } finally {
            //TODO 更新消息状态为consumed
            //FIXME: 每个Consumer一个Channel，不应该根据消息属性来定Channel
            String cluster = properties.getType();
            if(RabbitConstants.CLUSTER_MASTER.equals(cluster)){
                masterChannel.basicAck(envelope.getDeliveryTag(),false);
            }else{
                slaveChannel.basicAck(envelope.getDeliveryTag(),false);
            }
        }
    }

    @Override
    public void handleCancel(String consumerTag) throws IOException {

    }

    @Override
    public void handleConsumeOk(String consumerTag) {

    }

    @Override
    public void handleCancelOk(String consumerTag) {

    }

    @Override
    public void handleShutdownSignal(String consumerTag, ShutdownSignalException sig) {

    }

    @Override
    public void handleRecoverOk(String consumerTag) {

    }

    public RabbitConfig getRabbitConfig() {
        return rabbitConfig;
    }

    public Channel getMasterChannel() {
        return masterChannel;
    }

    public void setMasterChannel(Channel masterChannel) {
        this.masterChannel = masterChannel;
    }

    public String getQueueCode() {
        return queueCode;
    }

    public void setQueueCode(String queueCode) {
        this.queueCode = queueCode;
    }

    public Channel getSlaveChannel() {
        return slaveChannel;
    }

    public void setSlaveChannel(Channel slaveChannel) {
        this.slaveChannel = slaveChannel;
    }

    public void setRabbitConfig(RabbitConfig rabbitConfig) {
        this.rabbitConfig = rabbitConfig;
    }

    public DispatchCallbackService getDispatchCallbackService() {
        return dispatchCallbackService;
    }

    public void setDispatchCallbackService(DispatchCallbackService dispatchCallbackService) {
        this.dispatchCallbackService = dispatchCallbackService;
    }
}
