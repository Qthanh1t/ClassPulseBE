package com.classpulse.session;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RaisedHandRepository extends JpaRepository<RaisedHand, UUID> {}
