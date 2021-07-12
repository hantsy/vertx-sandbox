package com.example.demo;

import io.smallrye.mutiny.Uni;
import io.vertx.sqlclient.Row;
import org.hibernate.reactive.mutiny.Mutiny;
import org.springframework.stereotype.Component;

import javax.persistence.criteria.*;
import javax.transaction.Transactional;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Logger;

@Component
public class PostRepository {
    private static final Logger LOGGER = Logger.getLogger(PostRepository.class.getName());

    private Mutiny.SessionFactory sessionFactory;
    private Mutiny.Session session;

    public PostRepository(Mutiny.SessionFactory sessionFactory) {
        this.sessionFactory = sessionFactory;
        this.session = this.sessionFactory.openSession();
    }

    public Uni<List<Post>> findAll() {
        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        // create query
        CriteriaQuery<Post> query = cb.createQuery(Post.class);
        // set the root class
        Root<Post> root = query.from(Post.class);
        return this.session.createQuery(query).getResultList();
    }

    public Uni<List<Post>> findByKeyword(String q, int offset, int limit) {

        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        // create query
        CriteriaQuery<Post> query = cb.createQuery(Post.class);
        // set the root class
        Root<Post> root = query.from(Post.class);

        // if keyword is provided
        if (q != null && !q.trim().isEmpty()) {
            query.where(
                cb.or(
                    cb.like(root.get(Post_.title), "%" + q + "%"),
                    cb.like(root.get(Post_.content), "%" + q + "%")
                )
            );
        }
        //perform query
        return this.session.createQuery(query)
            .setFirstResult(offset)
            .setMaxResults(limit)
            .getResultList();
    }


    public Uni<Post> findById(UUID id) {
        Objects.requireNonNull(id, "id can not be null");
        return this.session.find(Post.class, id);
    }

    public Uni<Post> save(Post post) {
        if (post.getId() == null) {
            this.session.persist(post);
            return Uni.createFrom().item(post);
        } else {
            return this.session.merge(post);
        }
    }

    public Uni<Post[]> saveAll(List<Post> data) {
        Post[] array = data.toArray(new Post[0]);
        this.session.persistAll(array);
        return Uni.createFrom().item(array);
    }

//    @Transactional
//    public Uni<Integer> updateStatus(UUID id, Post.Status status) {
//        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
//        // create update
//        CriteriaUpdate<Post> delete = cb.createCriteriaUpdate(Post.class);
//        // set the root class
//        Root<Post> root = delete.from(Post.class);
//        // set where clause
//        delete.set(root.get(Post_.status), status);
//        delete.where(cb.equal(root.get(Post_.id), id));
//        // perform update
//        return this.session.createQuery(delete).executeUpdate();
//    }

    public Uni<Integer> deleteById(UUID id) {
        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        // create delete
        CriteriaDelete<Post> delete = cb.createCriteriaDelete(Post.class);
        // set the root class
        Root<Post> root = delete.from(Post.class);
        // set where clause
        delete.where(cb.equal(root.get(Post_.id), id));
        // perform update
        return this.sessionFactory.withTransaction((session, tx) ->
            session.createQuery(delete).executeUpdate()
        );
    }

    public Uni<Integer> deleteAll() {
        CriteriaBuilder cb = this.sessionFactory.getCriteriaBuilder();
        // create delete
        CriteriaDelete<Post> delete = cb.createCriteriaDelete(Post.class);
        // set the root class
        Root<Post> root = delete.from(Post.class);
        // perform update
        return this.sessionFactory.withTransaction((session, tx) ->
            session.createQuery(delete).executeUpdate()
        );
    }

}
