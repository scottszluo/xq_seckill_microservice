package net.lovexq.seckill.background.estate.service.impl;

import net.lovexq.seckill.background.core.model.PageX;
import net.lovexq.seckill.background.core.repository.cache.ByteRedisClient;
import net.lovexq.seckill.background.domain.estate.dto.EstateItemDTO;
import net.lovexq.seckill.background.domain.estate.model.EstateImageModel;
import net.lovexq.seckill.background.domain.estate.model.EstateItemModel;
import net.lovexq.seckill.background.estate.repository.EstateImageRepository;
import net.lovexq.seckill.background.estate.repository.EstateItemRepository;
import net.lovexq.seckill.background.estate.repository.specification.EstateItemSpecification;
import net.lovexq.seckill.background.estate.service.EstateService;
import net.lovexq.seckill.common.utils.CacheKeyGenerator;
import net.lovexq.seckill.common.utils.CachedBeanCopier;
import net.lovexq.seckill.common.utils.TimeUtil;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Example;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 房源业务层实现类
 *
 * @author LuPindong
 * @time 2017-04-20 23:05
 */
@Service
public class EstateServiceImpl implements EstateService {

    private static final Logger LOGGER = LoggerFactory.getLogger(EstateServiceImpl.class);
    @Autowired
    private EstateItemRepository estateItemRepository;
    @Autowired
    private EstateImageRepository estateImageRepository;
    @Autowired
    private ByteRedisClient byteRedisClient;

    @Override
    @Transactional(readOnly = true)
    public Page<EstateItemDTO> listForSaleByPage(Pageable pageable, Map<String, Object> paramMap) throws Exception {
        String cacheKey = CacheKeyGenerator.generate(EstateItemDTO.class, "listForSaleByPage", pageable, paramMap);

        Page<EstateItemDTO> targetItemPage = new PageX();

        // 读取缓存数据
        targetItemPage = byteRedisClient.getByteObj(cacheKey, targetItemPage.getClass());
        if (targetItemPage != null && CollectionUtils.isNotEmpty(targetItemPage.getContent())) {
            return new PageX(targetItemPage.getContent(), pageable, targetItemPage.getTotalElements());
        } else {
            Page<EstateItemModel> sourceItemPage = estateItemRepository.findAll(EstateItemSpecification.getForSaleSpec(paramMap), pageable);
            List<EstateItemModel> sourceItemList = sourceItemPage.getContent();

            if (CollectionUtils.isNotEmpty(sourceItemList)) {
                List<EstateItemDTO> targetItemList = new ArrayList();
                for (EstateItemModel model : sourceItemList) {
                    EstateItemDTO dto = new EstateItemDTO();
                    CachedBeanCopier.copy(model, dto);
                    if (StringUtils.isBlank(dto.getCoverUrl())) dto.setCoverUrl("/3rd-party/porto/img/blank.jpg");
                    dto.setDetailHref("/estate/" + dto.getHouseCode() + ".shtml");
                    dto.setTotalPriceStr(dto.getTotalPrice() + "万");
                    dto.setUnitPriceStr("单价" + dto.getUnitPrice() + "万");
                    dto.setDownPayments(dto.getUnitPriceStr() + ", 首付" + BigDecimal.valueOf(0.3d).multiply(dto.getTotalPrice()).setScale(2, BigDecimal.ROUND_HALF_DOWN) + "万");
                    dto.setAreaStr(dto.getArea() + "平米");
                    dto.setFocusNumStr(dto.getFocusNum() + "人关注");
                    dto.setWatchNumStr(dto.getWatchNum() + "次带看");
                    dto.setNewEstate(dto.getUpdateTime().isAfter(TimeUtil.nowDateTime().minusDays(3))); // 是当前日期三天前发布的
                    targetItemList.add(dto);
                }
                targetItemPage = new PageX(targetItemList, pageable, sourceItemPage.getTotalElements());

                // 数据写入缓存
                byteRedisClient.setByteObj(cacheKey, targetItemPage, 3600);
            }

            return targetItemPage;
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<EstateImageModel> listByHouseCode(String houseCode) {
        EstateImageModel estateImage = new EstateImageModel(null, houseCode);
        Sort sort = new Sort(Sort.Direction.ASC, "pictureType");
        return estateImageRepository.findAll(Example.of(estateImage), sort);
    }

    @Override
    public List<EstateItemModel> findTop20ByHouseCodeLikeAndSaleState(String targetCode, String saleState) {
        return estateItemRepository.findTop20ByHouseCodeLikeAndSaleState(targetCode, saleState);
    }

    @Override
    public EstateItemModel save(EstateItemModel estateItem) {
        return estateItemRepository.save(estateItem);
    }
}