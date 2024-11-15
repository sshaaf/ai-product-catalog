package com.glroland.ai.catalog.product;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.glroland.ai.catalog.ragagent.ProductEmbedding;

@Component
public class ProductDAO {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public Product getProduct(int id)
    {
        String sql = "SELECT product_id, "
                          + "sku, "
                          + "brand_id, " 
                          + "product_name, "
                          + "product_desc, "
                          + "size, "
                          + "msrp, "
                          + "category_id "
                    + "FROM products "
                    + "WHERE product_id = ?";

        List<Product> products = (List<Product>)jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new Product(rs.getInt("product_id"), 
                                        rs.getString("sku"), 
                                        rs.getInt("brand_id"), 
                                        rs.getString("product_name"), 
                                        rs.getString("product_desc"), 
                                        rs.getString("size"), 
                                        rs.getDouble("msrp"), 
                                        rs.getInt("category_id")),
            id);

        if ((products == null) || (products.size() == 0))
            return null;

        if (products.size() > 1)
            throw new RuntimeException("More than one product found with ID.  Unexpected...  ProductID=" + id);

        return products.get(0);
    }

    public Product getProductBySKU(String sku)
    {
        String sql = "SELECT product_id, "
                          + "sku, "
                          + "brand_id, " 
                          + "product_name, "
                          + "product_desc, "
                          + "size, "
                          + "msrp, "
                          + "category_id "
                    + "FROM products "
                    + "WHERE UPPER(sku) = UPPER(?)";

        List<Product> products = (List<Product>)jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new Product(rs.getInt("product_id"), 
                                        rs.getString("sku"), 
                                        rs.getInt("brand_id"), 
                                        rs.getString("product_name"), 
                                        rs.getString("product_desc"), 
                                        rs.getString("size"), 
                                        rs.getDouble("msrp"), 
                                        rs.getInt("category_id")),
            sku);

        if ((products == null) || (products.size() == 0))
            return null;

        if (products.size() > 1)
            throw new RuntimeException("More than one product found with SKU.  Unexpected...  SKU=" + sku);

        return products.get(0);
    }

    public List<Product> search(String category, String brand, String sku, String size, String nameDesc)
    {
        StringBuffer sql = new StringBuffer();
        sql.append("SELECT product_id, "
                        + "sku, "
                        + "products.brand_id as brand_id, " 
                        + "product_name, "
                        + "product_desc, "
                        + "size, "
                        + "msrp, "
                        + "products.category_id as category_id "
                 + "FROM products, categories, brands " 
                 + "WHERE products.category_id = categories.category_id "
                 + "AND products.brand_id = brands.brand_id ");
        if ((category != null) && (category.length() > 0))
        {
            sql.append("AND UPPER(categories.category_desc) LIKE UPPER('%").append(category).append("%') ");
        }
        if ((brand != null) && (brand.length() > 0))
        {
            sql.append("AND UPPER(brand_desc) LIKE UPPER('%").append(brand).append("%') ");
        }
        if ((sku != null) && (sku.length() > 0))
        {
            sql.append("AND UPPER(sku) LIKE UPPER('%").append(sku.toUpperCase()).append("%') ");
        }
        if ((nameDesc != null) && (nameDesc.length() > 0))
        {
            sql.append("AND UPPER(product_name) LIKE UPPER('%").append(nameDesc.toUpperCase()).append("%') OR ");
            sql.append("UPPER(product_desc) LIKE UPPER('%").append(nameDesc.toUpperCase()).append("%') ");
        }

        return (List<Product>)jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> new Product(rs.getInt("product_id"), 
                                        rs.getString("sku"), 
                                        rs.getInt("brand_id"), 
                                        rs.getString("product_name"), 
                                        rs.getString("product_desc"), 
                                        rs.getString("size"), 
                                        rs.getDouble("msrp"), 
                                        rs.getInt("category_id")
                                        )
            );
    }

    public List<SimilarProduct> similaritySearch(float [] embedding, int limit)
    {
        StringBuffer sql = new StringBuffer();
        sql.append("SELECT products.product_id, "
                        + "sku, "
                        + "products.brand_id as brand_id, " 
                        + "product_name, "
                        + "product_desc, "
                        + "size, "
                        + "msrp, "
                        + "products.category_id as category_id, "
                        + "embedding <-> CAST(? as vector) as distance, "
                        + "1 - (embedding <=> CAST(? as vector)) as cosign_similarity, "
                        + "(embedding <#> CAST(? as vector)) * -1 AS inner_product, "
                        + "text_segment "
                 + "FROM products, categories, brands, product_embeddings " 
                 + "WHERE products.category_id = categories.category_id "
                 + "AND products.brand_id = brands.brand_id "
                 + "AND product_embeddings.product_id = products.product_id "
                 + "ORDER BY embedding <-> CAST(? as vector) "
                 + "LIMIT ?");

        List<SimilarProduct> products = (List<SimilarProduct>)jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> new SimilarProduct(
                                        rs.getInt("product_id"), 
                                        rs.getString("sku"), 
                                        rs.getInt("brand_id"), 
                                        rs.getString("product_name"), 
                                        rs.getString("product_desc"), 
                                        rs.getString("size"), 
                                        rs.getDouble("msrp"), 
                                        rs.getInt("category_id"),
                                        rs.getDouble("distance"),
                                        rs.getDouble("cosign_similarity"),
                                        rs.getDouble("inner_product"),
                                        rs.getString("text_segment")),
            new Object[] { embedding, embedding, embedding, embedding, limit });

        return products;
    }

    public List<ProductEmbedding> getEmbeddingsForProduct(int productId)
    {
        StringBuffer sql = new StringBuffer();
        sql.append("SELECT product_id, "
                        + "model, " 
                        + "text_segment, "
                        + "embedding "
                 + "FROM product_embeddings " 
                 + "WHERE product_id = ? ");

        List<ProductEmbedding> productEmbeddings = (List<ProductEmbedding>)jdbcTemplate.query(
            sql.toString(),
            (rs, rowNum) -> new ProductEmbedding(rs.getInt("product_id"), 
                                                 rs.getString("model"), 
                                                 rs.getString("text_segment"), 
                                                 rs.getString("embedding")),
            new Object[] { productId });

        return productEmbeddings;
    }
}
