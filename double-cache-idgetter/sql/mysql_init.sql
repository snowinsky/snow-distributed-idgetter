CREATE TABLE `t_seq_conf` (
    `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'pk',
    `name` VARCHAR(50) NOT NULL COMMENT 'seq key' COLLATE 'utf8mb4_0900_as_cs',
    `current_value` BIGINT(20) NOT NULL DEFAULT '1' COMMENT 'currenct buffer max-value + 1, which is update by java application before use',
    `increment_size` BIGINT(20) NOT NULL DEFAULT '1' COMMENT 'increment size, which is configure by java application before use increment in jvm',
    `status` CHAR(1) NOT NULL DEFAULT 'a' COLLATE 'utf8mb4_0900_as_cs',
    `cdate` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `edate` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `creator` VARCHAR(50) NOT NULL DEFAULT 'system' COLLATE 'utf8mb4_0900_as_cs',
    `editor` VARCHAR(50) NOT NULL DEFAULT 'system' COLLATE 'utf8mb4_0900_as_cs',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE INDEX `uk_seq_conf_name` (`name`) USING BTREE
    )
COMMENT='configure table, storage next buffer start value'
COLLATE='utf8mb4_0900_as_cs'
ENGINE=InnoDB
AUTO_INCREMENT=1
;