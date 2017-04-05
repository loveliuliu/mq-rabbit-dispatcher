package com.ymatou.mq.rabbit.dispatcher.service;

import com.ymatou.mq.infrastructure.filedb.FileDb;
import com.ymatou.mq.infrastructure.filedb.FileDbConfig;
import com.ymatou.mq.infrastructure.filedb.PutExceptionHandler;
import com.ymatou.mq.infrastructure.model.Message;
import com.ymatou.mq.infrastructure.service.MessageConfigService;
import com.ymatou.mq.infrastructure.service.MessageService;
import com.ymatou.mq.rabbit.config.RabbitConfig;
import com.ymatou.mq.rabbit.dispatcher.config.DispatchConfig;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.Optional;
import java.util.function.Function;

/**
 * message dispatch分发service
 * Created by zhangzhihua on 2017/4/1.
 */
@Component
public class MessageDispatchService{

    private static final Logger logger = LoggerFactory.getLogger(MessageDispatchService.class);

    @Autowired
    private FileQueueProcessorService fileQueueProcessorService;

    @Autowired
    private MessageService messageService;

    /**
     * 由接收站直接调用的分发处理接口
     * @param message
     */
    public boolean dispatch(Message message){
        //TODO 与recv 统一返回值还是异常?
        //写fileDb
        boolean result = fileQueueProcessorService.saveMessageToFileDb(message);
        //若写失败，则同步写mongo
        if(!result){
            try {
                return messageService.saveMessage(message);
            } catch (Exception e) {
                logger.error("save message to mongo error.",e);
                return false;
            }
        }
        return result;
    }


}
