-- 实验二造数脚本：翻倍法构建大表（替代 Python）
-- 运行：mysql -uroot -p123456 checkdata_agent < scripts/量化数据/gen_big_table.sql

DROP TABLE IF EXISTS exp2_big_table;

CREATE TABLE exp2_big_table (
    id INT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(100),
    category VARCHAR(50),
    amount DECIMAL(10,2),
    created_at DATETIME,
    INDEX idx_cat (category)
);

-- 种子数据：从现有业务表取数
INSERT INTO exp2_big_table (name, category, amount, created_at)
SELECT CONCAT('item_', c.customer_id), 'cat_a', 99.99, NOW() FROM customer c LIMIT 500;

INSERT INTO exp2_big_table (name, category, amount, created_at)
SELECT CONCAT('item_', f.film_id), 'cat_b', 19.99, NOW() FROM film f LIMIT 500;

INSERT INTO exp2_big_table (name, category, amount, created_at)
SELECT CONCAT('item_', a.actor_id), 'cat_c', 49.99, NOW() FROM actor a LIMIT 500;

-- 翻倍 12 次：1500 × 2^12 ≈ 6,144,000 行
SET @doubles = 12;
SET @i = 0;
WHILE @i < @doubles DO
    SET @sql = CONCAT('INSERT INTO exp2_big_table (name, category, amount, created_at) ',
                      'SELECT CONCAT(name, ''_dup', @i, '''), category, amount, created_at FROM exp2_big_table');
    PREPARE stmt FROM @sql;
    EXECUTE stmt;
    DEALLOCATE PREPARE stmt;
    SET @i = @i + 1;
END WHILE;

SELECT COUNT(*) AS total_rows FROM exp2_big_table;