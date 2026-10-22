package com.nexora.career;
import java.util.Optional;
public interface CareerRepository { void save(CareerProfile p); Optional<CareerProfile> find(String reg); }
