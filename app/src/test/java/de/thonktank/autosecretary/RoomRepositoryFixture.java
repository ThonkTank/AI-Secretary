package de.thonktank.autosecretary;

import de.thonktank.autosecretary.data.local.RoomCatalogRepository;
import de.thonktank.autosecretary.data.local.RoomFlowRepository;
import de.thonktank.autosecretary.data.local.RoomStepRepository;
import de.thonktank.autosecretary.data.local.RoomTodayRepository;
import de.thonktank.autosecretary.data.local.RoomTrainingRepository;
import de.thonktank.autosecretary.data.local.RoomTransactionRunner;
import de.thonktank.autosecretary.domain.repository.CatalogRepository;
import de.thonktank.autosecretary.domain.repository.FlowRepository;
import de.thonktank.autosecretary.domain.repository.StepRepository;
import de.thonktank.autosecretary.domain.repository.TodayRepository;
import de.thonktank.autosecretary.domain.repository.TrainingRepository;
import de.thonktank.autosecretary.domain.transaction.TransactionRunner;

/** Composes the five real Room adapters for cross-slice integration tests. */
final class RoomRepositoryFixture {
    final CatalogRepository catalog;
    final StepRepository steps;
    final TodayRepository today;
    final FlowRepository flows;
    final TrainingRepository training;
    final TransactionRunner transactions;

    RoomRepositoryFixture(AppDatabase database) {
        transactions = new RoomTransactionRunner(database);
        catalog = new RoomCatalogRepository(database);
        steps = new RoomStepRepository(database, transactions);
        today = new RoomTodayRepository(database);
        flows = new RoomFlowRepository(database);
        training = new RoomTrainingRepository(database);
    }
}
