package com.example.merrygoround.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.merrygoround.ui.activity.ActivityViewModel
import com.example.merrygoround.ui.auth.AuthViewModel
import com.example.merrygoround.ui.contributions.ContributeViewModel
import com.example.merrygoround.ui.dashboard.DashboardViewModel
import com.example.merrygoround.ui.groups.CreateGroupViewModel
import com.example.merrygoround.ui.groups.GroupDetailViewModel
import com.example.merrygoround.ui.groups.GroupsViewModel
import com.example.merrygoround.ui.groups.JoinGroupViewModel
import com.example.merrygoround.ui.payouts.PayoutScheduleViewModel
import com.example.merrygoround.ui.profile.NotificationsViewModel
import com.example.merrygoround.ui.profile.ProfileViewModel

/**
 * All ViewModel factories in one place — manual DI, no Hilt (documented in README).
 * Each VM receives only the repositories it needs.
 */
object ViewModels {

    fun auth(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { AuthViewModel(c.authRepository) }
    }

    fun groups(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { GroupsViewModel(c.groupRepository) }
    }

    fun createGroup(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { CreateGroupViewModel(c.groupRepository) }
    }

    fun joinGroup(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { JoinGroupViewModel(c.groupRepository) }
    }

    fun dashboard(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            DashboardViewModel(c.groupRepository, c.activityRepository, c.contributionRepository)
        }
    }

    fun groupDetail(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { GroupDetailViewModel(c.groupRepository, c.contributionRepository) }
    }

    fun contribute(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { ContributeViewModel(c.groupRepository, c.contributionRepository) }
    }

    fun payoutSchedule(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { PayoutScheduleViewModel(c.groupRepository) }
    }

    fun activity(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer {
            ActivityViewModel(c.activityRepository, c.contributionRepository, c.groupRepository)
        }
    }

    fun profile(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { ProfileViewModel(c.authRepository, c.activityRepository) }
    }

    fun notifications(c: AppContainer): ViewModelProvider.Factory = viewModelFactory {
        initializer { NotificationsViewModel(c.activityRepository) }
    }
}
