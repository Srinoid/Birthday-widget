package cz.krtinec.birthday.widgets;

import java.util.List;

import cz.krtinec.birthday.R;
import cz.krtinec.birthday.data.BirthdayProvider;
import cz.krtinec.birthday.dto.BContact;
import android.content.ComponentName;
import android.widget.RemoteViews;

public class UpdateService4x1 extends UpdateService {

	@Override
	public ComponentName getComponentName() {
		return new ComponentName(this, BirthdayWidget4x1.class);
	}

	@Override
	public RemoteViews updateViews() {
		RemoteViews views = new RemoteViews(this.getPackageName(), R.layout.widget4x1);
		List<BContact> list = BirthdayProvider.getInstance().upcomingBirthday(this);
		if (list.size() > 0) {
			BContact contact = list.get(0);
			views.setTextViewText(R.id.first_name, contact.getDisplayName());
			views.setTextViewText(R.id.first_date, contact.getDisplayDate(this));	
			replaceIconWithPhoto(views, contact, R.id.first_icon);					
		} else {
			views.setTextViewText(R.id.first_name, getText(R.string.no_name_found));				
		}
		if (list.size() > 1) {
			BContact contact = list.get(1);
			views.setTextViewText(R.id.second_name, contact.getDisplayName());
			views.setTextViewText(R.id.second_date, contact.getDisplayDate(this));
			replaceIconWithPhoto(views, contact, R.id.second_icon);
		} else {
			views.setTextViewText(R.id.second_name, getText(R.string.no_name_found));
		}
		if (list.size() > 2) {
			BContact contact = list.get(2);
			views.setTextViewText(R.id.third_name, contact.getDisplayName());
			views.setTextViewText(R.id.third_date, contact.getDisplayDate(this));
			replaceIconWithPhoto(views, contact, R.id.third_icon);
		} else {
			views.setTextViewText(R.id.third_name, getText(R.string.no_name_found));
		}
		if (list.size() > 3) {
			BContact contact = list.get(3);
			views.setTextViewText(R.id.fourth_name, contact.getDisplayName());
			views.setTextViewText(R.id.fourth_date, contact.getDisplayDate(this));
			replaceIconWithPhoto(views, contact, R.id.fourth_icon);
		} else {
			views.setTextViewText(R.id.fourth_name, getText(R.string.no_name_found));
		}

		return views;
	}

}
